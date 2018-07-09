/**
 * Created on 17.09.2002
 *
 *
 * myx - barachta */
package ru.myx.iface.logger;

import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;

import ru.myx.ae3.Engine;
import ru.myx.ae3.act.Act;
import java.util.function.Function;
import ru.myx.ae3.binary.Transfer;
import ru.myx.ae3.binary.TransferBuffer;
import ru.myx.ae3.binary.TransferCopier;
import ru.myx.ae3.binary.TransferSocket;
import ru.myx.ae3.binary.TransferTarget;
import ru.myx.ae3.exec.ExecProcess;
import ru.myx.ae3.flow.ObjectTarget;
import ru.myx.ae3.report.EventInterceptor;
import ru.myx.ae3.report.Report;
import ru.myx.io.DataInputByteArrayFast;

/**
 * @author myx
 * 
 * myx - barachta 
 *         "typecomment": Window>Preferences>Java>Templates. To enable and
 *         disable the creation of type comments go to
 *         Window>Preferences>Java>Code Generation.
 *         http://cloud.datashed.net/gems/starlady/TrillianPro.zip
 */

final class LoggerSocketHandler implements TransferTarget, TransferBuffer, ObjectTarget<byte[]> {

	static final int QBUFF_INIT = 1024;

	static final int QBUFF_STEP = 1024;

	static final int RBUFF_INIT = 4096;

	static int stBadRequests = 0;

	static int stExpands = 0;

	static int stRequests = 0;

	static int stUnexpectedFinalizations = 0;

	private static final KeepAliveReadConnector KEEP_ALIVE_CONNECTOR = new KeepAliveReadConnector();

	private static final int MAX_COMMAND_LENGTH = 128;

	private static final int MD_COMMAND = 0;

	private static final int MD_FIRSTBYTE = 1;

	private static final int MD_TELNET1 = 2;

	private static final int MD_TELNET2 = 3;

	private static final byte[] RESPONSE_INVALID_CHARACTER = "! invalid character\r\n".getBytes();

	private static final byte[] RESPONSE_TOO_LONG = "! too long\r\n".getBytes();

	private static final byte[] RESPONSE_DETACHED = "# watcher detached\r\n> ".getBytes();

	private static final byte[] RESPONSE_ATTACHED = "# watcher attach... ".getBytes();

	private static final byte[] RESPONSE_WELCOME = ("# Welcome to " + Engine.HOST_NAME + " running " + Engine.VERSION_STRING + "\r\n").getBytes();

	private final static byte[] TELNET_SETUP = new byte[]{
			//
			// DO SUPPRESS GO AHEAD
			(byte) 255, (byte) 253, (byte) 3,
			// WILL SUPPRESS GO AHEAD
			(byte) 255, (byte) 251, (byte) 3,
			// WILL ECHO
			(byte) 255, (byte) 251, (byte) 1,
			// DON'T LINEMODE
			(byte) 255, (byte) 254, (byte) 34,
			/**
			 * <code>
			// DO TEMINAL TYPE
			(byte) 255,
			(byte) 253,
			(byte) 24,
			// ASK TEMINAL TYPE
			(byte) 255,
			(byte) 250,
			(byte) 24,
			(byte) 1,
			(byte) 255,
			(byte) 240,
			// DO WINDOW SIZE
			(byte) 255,
			(byte) 253,
			(byte) 31,
			// ASK WINDOW SIZE
			(byte) 255,
			(byte) 250,
			(byte) 31,
			(byte) 1,
			(byte) 255,
			(byte) 240,
			</code>
			 **/
			// end
	};

	private final static byte[] TERMINAL_SETUP = new byte[]{
			//
			// RESET
			(byte) 27, (byte) 'c',
			// ERASE ENTIRE SCREEN
			(byte) 27, (byte) '[', (byte) '2', (byte) 'J',
			// STATUS REPORT
			(byte) 27, (byte) '[', (byte) '5', (byte) 'n',
			// WHAT ARE YOU
			(byte) 27, (byte) '[', (byte) 'c',
			// end
	};

	private final static void onError400() {

		LoggerSocketHandler.stBadRequests++;
		LoggerSocketHandler.stRequests++;
	}

	private char[] qBuffer;

	private int qBufferCapacity;

	private int qBufSize;

	private String qCommand;

	private int qLengthRemaining;

	private int qMode;

	private final int queueIndex;

	private boolean active;

	private byte[] rBuffer;

	private int rBufferLength;

	private int rBufferPosition;

	private TransferSocket socket;

	private String sourcePeerAddress;

	private String sourcePeerIdentity;

	LoggerSocketHandler(final int queueIndex) {
		this.queueIndex = queueIndex;
		this.qBuffer = new char[LoggerSocketHandler.QBUFF_INIT];
		this.qBufferCapacity = LoggerSocketHandler.QBUFF_INIT;
		this.rBuffer = new byte[LoggerSocketHandler.RBUFF_INIT];
	}

	@Override
	public final void abort(final String reason) {

		EventInterceptor.doUnRegister(this);
		this.qCommand = null;
		this.socket = null;
		this.sourcePeerAddress = null;
		this.sourcePeerIdentity = null;
		final TransferSocket socket = this.socket;
		if (socket != null) {
			socket.abort(reason);
			this.socket = null;
			this.destroy();
		}
	}

	@Override
	public boolean absorb(final byte[] object) {

		return this.socket.getTarget().absorbArray(object, 0, object.length);
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final boolean absorb(final int i) {

		return this.consumeNext(i);
	}

	@Override
	public final boolean absorbArray(final byte[] bytes, final int off, final int length) {

		for (int i = 0; i < length; ++i) {
			if (!this.consumeNext(bytes[off + i] & 0xFF)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public final boolean absorbBuffer(final TransferBuffer buffer) {

		if (buffer.isDirectAbsolutely()) {
			final byte[] bytes = buffer.toDirectArray();
			return this.absorbArray(bytes, 0, bytes.length);
		}
		while (buffer.hasRemaining()) {
			if (buffer.isSequence()) {
				final TransferBuffer next = buffer.nextSequenceBuffer();
				if (!this.absorbBuffer(next)) {
					return false;
				}
			} else {
				for (;;) {
					final long length = buffer.remaining();
					if (length == 0) {
						return true;
					}
					if (!this.consumeNext(buffer.next())) {
						return false;
					}
				}
			}
		}
		return true;
	}

	@Override
	public final boolean absorbNio(final ByteBuffer buffer) {

		for (int i = buffer.remaining(); i > 0; --i) {
			if (!this.consumeNext(buffer.get() & 0xFF)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Class<? extends byte[]> accepts() {

		return byte[].class;
	}

	private final boolean append(final int b) {

		if (this.qLengthRemaining == 0) {
			this.socket.getTarget().absorbArray(LoggerSocketHandler.RESPONSE_TOO_LONG, 0, LoggerSocketHandler.RESPONSE_TOO_LONG.length);
			Logger.LOG.event(Logger.PNAME_DMESG, "INFO", "BAD REQUEST: " + "length exceeded, mode=" + this.qMode + ", remote=" + this.sourcePeerIdentity);
			LoggerSocketHandler.onError400();
			this.close();
			return false;
		}
		if (this.qBufSize + 1 == this.qBufferCapacity) {
			final char[] newBuf = new char[this.qBufferCapacity += LoggerSocketHandler.QBUFF_STEP];
			System.arraycopy(this.qBuffer, 0, newBuf, 0, this.qBufSize);
			this.qBuffer = newBuf;
			LoggerSocketHandler.stExpands++;
		}
		this.qBuffer[this.qBufSize++] = (char) b;
		return --this.qLengthRemaining > 0;
	}

	@Override
	public final void close() {

		EventInterceptor.doUnRegister(this);
		final TransferSocket socket = this.socket;
		if (socket != null) {
			socket.close();
			this.socket = null;
			this.destroy();
		}
	}

	private final boolean consumeNext(final int b) {

		switch (this.qMode) {
			case MD_COMMAND :
				if (b == 0) {
					// nop
					return true;
				}
				if (b == '\r' || b == '\n') {
					this.qCommand = this.setMode(LoggerSocketHandler.MD_FIRSTBYTE, LoggerSocketHandler.MAX_COMMAND_LENGTH);
					this.headAppendCRLF();
					if (!(this.headSend() && this.onDoneRead())) {
						return false;
					}
					this.headAppend(LoggerSocketHandler.RESPONSE_ATTACHED);
					this.headSend();
					EventInterceptor.doRegister(this);
					return true;
				}
				if (b >= 0 && b < ' ' || b > 127) {
					this.socket.getTarget().absorbArray(LoggerSocketHandler.RESPONSE_INVALID_CHARACTER, 0, LoggerSocketHandler.RESPONSE_INVALID_CHARACTER.length);
					Logger.LOG.event(
							Logger.PNAME_DMESG,
							"INFO",
							"BAD REQUEST: " + "illegal character in command, code=" + b + ", collected=" + this.setMode(0, 0) + ", remote=" + this.sourcePeerIdentity);
					LoggerSocketHandler.onError400();
					this.close();
					return false;
				}
				this.socket.getTarget().absorb(b);
				this.socket.getTarget().force();
				return this.append(b);
			case MD_FIRSTBYTE :
				if (b == 0) {
					// nop
					return true;
				}
				if (b == 255) {
					this.qMode = LoggerSocketHandler.MD_TELNET1;
					return --this.qLengthRemaining > 0;
				}
				if (b == '\r' || b == '\n' || b == ' ') {
					this.socket.getTarget().absorb(b);
					this.socket.getTarget().force();
					return --this.qLengthRemaining > 0;
				}
				if (b >= 0 && b <= ' ' || b > 127) {
					this.socket.getTarget().absorbArray(LoggerSocketHandler.RESPONSE_INVALID_CHARACTER, 0, LoggerSocketHandler.RESPONSE_INVALID_CHARACTER.length);
					Logger.LOG.event(Logger.PNAME_DMESG, "INFO", "BAD REQUEST: " + "illegal character in first byte, code=" + b + ", remote=" + this.sourcePeerIdentity);
					LoggerSocketHandler.onError400();
					this.close();
					return false;
				}
				this.qMode = LoggerSocketHandler.MD_COMMAND;
				this.headAppend(LoggerSocketHandler.RESPONSE_DETACHED);
				this.headSend();
				EventInterceptor.doUnRegister(this);
				this.socket.getTarget().absorb(b);
				this.socket.getTarget().force();
				return this.append(b);
			case MD_TELNET1 :
				if (b >= 250 && b <= 255) {
					this.qMode = LoggerSocketHandler.MD_TELNET2;
					return --this.qLengthRemaining > 0;
				}
				this.qMode = LoggerSocketHandler.MD_FIRSTBYTE;
				return true;
			case MD_TELNET2 :
				this.qMode = LoggerSocketHandler.MD_FIRSTBYTE;
				return --this.qLengthRemaining > 0;
			default :
				return false;
		}
	}

	@Override
	public final void destroy() {

		if (this.active && this.socket == null) {
			this.active = false;
			LoggerHandlerQueue.reuseParser(this, this.queueIndex);
		}
		this.rBufferPosition = 0;
	}

	@Override
	public <A, R> boolean enqueueAction(final ExecProcess ctx, final Function<A, R> function, final A argument) {

		Act.launch(ctx, function, argument);
		return true;
	}

	@Override
	protected final void finalize() throws Throwable {

		if (this.socket != null) {
			if (this.socket.isOpen()) {
				try {
					this.socket.abort("Finalized");
				} catch (final Throwable t) {
					// ignore
				}
				LoggerSocketHandler.stUnexpectedFinalizations++;
				Logger.LOG.event(Logger.PNAME_DMESG, "FINALIZE", "Unexpected http request finalization - non closed socket!" + this.sourcePeerIdentity);
			} else {
				LoggerSocketHandler.stUnexpectedFinalizations++;
				Logger.LOG.event(Logger.PNAME_DMESG, "FINALIZE", "Unexpected http request finalization - non null socket! remote=" + this.sourcePeerIdentity);
			}
			this.socket = null;
		}
		super.finalize();
	}

	@Override
	public final void force() {

		// ignore
	}

	@Override
	public MessageDigest getMessageDigest() {

		final MessageDigest digest = Engine.getMessageDigestInstance();
		digest.update(this.rBuffer, this.rBufferPosition, this.rBufferLength);
		return digest;
	}

	@Override
	public final boolean hasRemaining() {

		return this.rBufferLength - this.rBufferPosition > 0;
	}

	private final void headAppend(final byte str[]) {

		final int newCount = this.rBufferLength + str.length;
		if (newCount > this.rBuffer.length) {
			this.headExpand(newCount);
		}
		System.arraycopy(str, 0, this.rBuffer, this.rBufferLength, str.length);
		this.rBufferLength = newCount;
	}

	private final void headAppend(final String str) {

		if (str == null) {
			this.headAppend("null");
			return;
		}
		final int len = str.length();
		if (len == 0) {
			return;
		}
		if (len < 32) {
			final int newCount = this.rBufferLength + len;
			if (newCount > this.rBuffer.length) {
				this.headExpand(newCount);
			}
			for (int i = 0; i < len; ++i) {
				this.rBuffer[this.rBufferLength++] = (byte) str.charAt(i);
			}
		} else {
			this.headAppend(str.getBytes());
		}
	}

	private final void headAppendCRLF() {

		final int newCount = this.rBufferLength + 2;
		if (newCount > this.rBuffer.length) {
			this.headExpand(newCount);
		}
		this.rBuffer[this.rBufferLength++] = '\r';
		this.rBuffer[this.rBufferLength++] = '\n';
	}

	private final void headExpand(final int minimumCapacity) {

		int newCapacity = (this.rBuffer.length + 1) * 2;
		if (newCapacity < 0) {
			newCapacity = Integer.MAX_VALUE;
		} else //
		if (minimumCapacity > newCapacity) {
			newCapacity = minimumCapacity;
		}
		final byte newValue[] = new byte[newCapacity];
		System.arraycopy(this.rBuffer, 0, newValue, 0, this.rBufferLength);
		this.rBuffer = newValue;
	}

	private final boolean headSend() {

		final byte[] bytes = this.toDirectArray();
		try {
			return this.socket.getTarget().absorbArray(bytes, 0, bytes.length);
		} finally {
			this.socket.getTarget().force();
		}
	}

	@Override
	public final boolean isDirectAbsolutely() {

		return this.rBufferPosition == 0 && this.rBufferLength == this.rBuffer.length;
	}

	@Override
	public final boolean isSequence() {

		return false;
	}

	final boolean isSocketPresentAndOpen() {

		if (this.socket == null) {
			return false;
		}
		if (this.socket.isOpen()) {
			return true;
		}
		this.socket.abort("Socket Closed");
		this.socket = null;
		return false;
	}

	@Override
	public final int next() {

		return this.rBuffer[this.rBufferPosition++] & 0xFF;
	}

	@Override
	public final int next(final byte[] buffer, final int offset, final int length) {

		final int amount = Math.min(this.rBufferLength - this.rBufferPosition, length);
		if (amount > 0) {
			System.arraycopy(this.rBuffer, this.rBufferPosition, buffer, offset, amount);
			this.rBufferPosition += amount;
		}
		return amount;
	}

	@Override
	public final TransferBuffer nextSequenceBuffer() {

		throw new UnsupportedOperationException("Not a sequence!");
	}

	private final boolean onDoneRead() {

		this.socket.getSource().connectTarget(null);
		try {
			if ("exit".equals(this.qCommand)) {
				this.headAppend("bye!");
				this.headAppendCRLF();
				this.headSend();
				return false;
			}
			if ("init".equals(this.qCommand)) {
				this.headAppend(LoggerSocketHandler.TELNET_SETUP);
				this.headAppend(LoggerSocketHandler.TERMINAL_SETUP);
				this.headSend();
			} else {
				this.headAppend("! unknown command: " + this.qCommand);
				this.headAppendCRLF();
				this.headSend();
			}
			this.socket.getTarget().force();
			return this.socket.getTarget().enqueueAction(Logger.CTX, LoggerSocketHandler.KEEP_ALIVE_CONNECTOR, this);
			// return true;
		} catch (final RuntimeException e) {
			throw e;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (Report.MODE_DEBUG) {
				Logger.LOG.event(Logger.PNAME_DMESG, "DEBUG", "REQUEST " + this.qCommand);
			}
		}
	}

	final void prepare(final TransferSocket socket) {

		this.socket = socket;
		this.sourcePeerAddress = socket.getRemoteAddress();
		this.sourcePeerIdentity = socket.getIdentity();
		this.qMode = LoggerSocketHandler.MD_FIRSTBYTE;
		this.qLengthRemaining = LoggerSocketHandler.MAX_COMMAND_LENGTH;
		this.qBufSize = 0;
		this.headAppend(LoggerSocketHandler.RESPONSE_WELCOME);
		this.headAppend("# ");
		this.headAppend(LoggerSocketHandler.TELNET_SETUP);
		this.headAppend("your address is: ");
		this.headAppend(this.sourcePeerAddress);
		this.headAppendCRLF();
		this.headAppend(LoggerSocketHandler.RESPONSE_ATTACHED);
		this.headSend();
		Logger.LOG.event(Logger.PNAME_DMESG, "INFO", "connection: " + socket);
		EventInterceptor.doRegister(this);
	}

	final void reconnect() {

		if (this.socket != null && this.socket.isOpen()) {
			this.socket.getSource().connectTarget(this);
		}
	}

	@Override
	public final long remaining() {

		return this.rBufferLength - this.rBufferPosition;
	}

	private final String setMode(final int mode, final int maxLength) {

		try {
			return new String(this.qBuffer, 0, this.qBufSize);
		} finally {
			this.qBufSize = 0;
			this.qMode = mode;
			this.qLengthRemaining = maxLength;
		}
	}

	@Override
	public final TransferCopier toBinary() {

		return Transfer.createCopier(this.rBuffer, this.rBufferPosition, this.rBufferLength - this.rBufferPosition);
	}

	@Override
	public final byte[] toDirectArray() {

		if (this.rBufferPosition == 0 && this.rBufferLength == this.rBuffer.length) {
			this.rBufferPosition = this.rBufferLength;
			return this.rBuffer;
		}
		final int remaining = this.rBufferLength - this.rBufferPosition;
		final byte[] result = new byte[remaining];
		System.arraycopy(this.rBuffer, this.rBufferPosition, result, 0, remaining);
		this.rBufferPosition = this.rBufferLength;
		return result;
	}

	@Override
	public final DataInputByteArrayFast toInputStream() {

		return new DataInputByteArrayFast(this.toDirectArray());
	}

	@Override
	public final TransferBuffer toNioBuffer(final ByteBuffer target) {

		final int remaining = this.rBufferLength - this.rBufferPosition;
		if (remaining <= 0) {
			this.destroy();
			return null;
		}
		final int writable = target.remaining();
		if (writable <= 0) {
			return this;
		}
		if (writable >= remaining) {
			target.put(this.rBuffer, this.rBufferPosition, remaining);
			this.rBufferPosition = this.rBufferLength;
			this.destroy();
			return null;
		}
		target.put(this.rBuffer, this.rBufferPosition, writable);
		this.rBufferPosition += writable;
		return this;
	}

	@Override
	public final InputStreamReader toReaderUtf8() {

		return new InputStreamReader(this.toInputStream(), Engine.CHARSET_UTF8);
	}

	@Override
	public final String toString() {

		return "DMESG PARSER TARGET(" + System.identityHashCode(this) + ")";
	}

	@Override
	public final String toString(final Charset charset) {

		return new String(this.rBuffer, 0, this.rBufferLength, charset);
	}

	@Override
	public final String toString(final String charset) throws UnsupportedEncodingException {

		return new String(this.rBuffer, 0, this.rBufferLength, charset);
	}

	@Override
	public final TransferBuffer toSubBuffer(final long start, final long end) {

		final int remaining = this.rBufferLength - this.rBufferPosition;
		if (start < 0 || start > end || end > remaining) {
			throw new IllegalArgumentException("Indexes are out of bounds: start=" + start + ", end=" + end + ", length=" + remaining);
		}
		this.rBufferLength = (int) (this.rBufferPosition + end);
		this.rBufferPosition += start;
		return this;
	}

	@Override
	public MessageDigest updateMessageDigest(final MessageDigest digest) {

		digest.update(this.rBuffer, this.rBufferPosition, this.rBufferLength);
		return digest;
	}
}
