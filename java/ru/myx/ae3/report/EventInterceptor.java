/**
 * 
 */
package ru.myx.ae3.report;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import ru.myx.ae3.Engine;
import ru.myx.ae3.act.Act;
import ru.myx.ae3.act.ActService;
import ru.myx.ae3.base.BaseObject;
import ru.myx.ae3.flow.ObjectTarget;
import ru.myx.ae3.status.StatusInfo;
import ru.myx.util.FifoQueueMultithreadEnqueue;
import ru.myx.util.FifoQueueServiceMultithreadSwitching;

/**
 * @author myx
 * 
 */
public final class EventInterceptor extends LogReceiver implements ActService {
	
	private static Set<ObjectTarget<byte[]>>	targets		= new HashSet<>();
	
	private static EventInterceptor				INSTANCE	= new EventInterceptor();
	
	private static final Field					FIELD_SPY;
	static {
		/**
		 * Just for compiler check for this field
		 */
		if (BaseObject.NULL.baseValue() != null) { // false
			ReceiverMultiple.SPY = null;
		}
		/**
		 * We have to use reflection
		 */
		try {
			final Field field = ReceiverMultiple.class.getDeclaredField( "SPY" );
			assert field != null : "SPY field expected!";
			field.setAccessible( true );
			FIELD_SPY = field;
		} catch (final SecurityException e) {
			throw new RuntimeException( e );
		} catch (final NoSuchFieldException e) {
			throw new RuntimeException( e );
		}
	}
	
	/**
	 * @param target
	 * @return
	 */
	public static final boolean doRegister(final ObjectTarget<byte[]> target) {
		synchronized (EventInterceptor.targets) {
			try {
				try {
					return target.absorb( "done.\r\n".getBytes( Engine.CHARSET_UTF8 ) )
							&& EventInterceptor.targets.add( target );
				} catch (final RuntimeException e) {
					throw e;
				} catch (final Exception e) {
					throw new RuntimeException( e );
				}
				
			} finally {
				try {
					EventInterceptor.FIELD_SPY.set( null, EventInterceptor.targets.isEmpty()
							? null
							: EventInterceptor.INSTANCE );
				} catch (final IllegalArgumentException e) {
					throw new RuntimeException( e );
				} catch (final IllegalAccessException e) {
					throw new RuntimeException( e );
				}
			}
		}
	}
	
	/**
	 * @param target
	 * @return
	 */
	public static final boolean doUnRegister(final ObjectTarget<byte[]> target) {
		synchronized (EventInterceptor.targets) {
			try {
				return EventInterceptor.targets.remove( target );
			} finally {
				try {
					EventInterceptor.FIELD_SPY.set( null, EventInterceptor.targets.isEmpty()
							? null
							: EventInterceptor.INSTANCE );
				} catch (final IllegalArgumentException e) {
					throw new RuntimeException( e );
				} catch (final IllegalAccessException e) {
					throw new RuntimeException( e );
				}
			}
		}
	}
	
	/**
	 * @param data
	 */
	public static final void statusFillStatic(final StatusInfo data) {
		EventInterceptor.INSTANCE.statusFill( data );
	}
	
	private boolean													destroyed	= false;
	
	private final FifoQueueServiceMultithreadSwitching<Event>	queue		= new FifoQueueServiceMultithreadSwitching<>();
	
	private volatile int											stsLoops;
	
	private volatile int											stsUnhandled;
	
	private byte[]													rBuffer		= new byte[2048];
	
	private int														rBufferLength;
	
	private static final byte[]										DIGIT_ONES	= {
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',																};
	
	private static final byte[]										DIGIT_TENS	= {
			'0',
			'0',
			'0',
			'0',
			'0',
			'0',
			'0',
			'0',
			'0',
			'0',
			'1',
			'1',
			'1',
			'1',
			'1',
			'1',
			'1',
			'1',
			'1',
			'1',
			'2',
			'2',
			'2',
			'2',
			'2',
			'2',
			'2',
			'2',
			'2',
			'2',
			'3',
			'3',
			'3',
			'3',
			'3',
			'3',
			'3',
			'3',
			'3',
			'3',
			'4',
			'4',
			'4',
			'4',
			'4',
			'4',
			'4',
			'4',
			'4',
			'4',
			'5',
			'5',
			'5',
			'5',
			'5',
			'5',
			'5',
			'5',
			'5',
			'5',
			'6',
			'6',
			'6',
			'6',
			'6',
			'6',
			'6',
			'6',
			'6',
			'6',
			'7',
			'7',
			'7',
			'7',
			'7',
			'7',
			'7',
			'7',
			'7',
			'7',
			'8',
			'8',
			'8',
			'8',
			'8',
			'8',
			'8',
			'8',
			'8',
			'8',
			'9',
			'9',
			'9',
			'9',
			'9',
			'9',
			'9',
			'9',
			'9',
			'9',																};
	
	private static final byte[]										DIGITS		= {
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'a',
			'b',
			'c',
			'd',
			'e',
			'f',
			'g',
			'h',
			'i',
			'j',
			'k',
			'l',
			'm',
			'n',
			'o',
			'p',
			'q',
			'r',
			's',
			't',
			'u',
			'v',
			'w',
			'x',
			'y',
			'z'																};
	
	// Requires positive x
	private static final int stringSizeOfLong(final long x) {
		long p = 10;
		for (int i = 1; i < 19; ++i) {
			if (x < p) {
				return i;
			}
			p *= 10;
		}
		return 19;
	}
	
	private EventInterceptor() {
		Act.launchService( null, this );
	}
	
	@Override
	protected String[] eventClasses() {
		return null;
	}
	
	@Override
	protected String[] eventTypes() {
		return null;
	}
	
	private final void headAppend(final int c) {
		final int newCount = this.rBufferLength + 1;
		if (newCount > this.rBuffer.length) {
			this.headExpand( newCount );
		}
		this.rBuffer[this.rBufferLength++] = (byte) c;
	}
	
	final void headAppend(long l) {
		final int appendedLength = l < 0
				? EventInterceptor.stringSizeOfLong( -l ) + 1
				: EventInterceptor.stringSizeOfLong( l );
		final int spaceNeeded = this.rBufferLength + appendedLength;
		if (spaceNeeded > this.rBuffer.length) {
			this.headExpand( spaceNeeded );
		}
		long q;
		int r;
		int charPos = spaceNeeded;
		byte sign = 0;
		if (l < 0) {
			sign = '-';
			l = -l;
		}
		// Get 2 digits/iteration using longs until quotient fits into an int
		while (l > Integer.MAX_VALUE) {
			q = l / 100;
			r = (int) (l - q * 100);
			l = q;
			this.rBuffer[--charPos] = EventInterceptor.DIGIT_ONES[r];
			this.rBuffer[--charPos] = EventInterceptor.DIGIT_TENS[r];
		}
		// Get 2 digits/iteration using ints
		int q2;
		int i2 = (int) l;
		while (i2 >= 65536) {
			q2 = i2 / 100;
			r = i2 - q2 * 100;
			i2 = q2;
			this.rBuffer[--charPos] = EventInterceptor.DIGIT_ONES[r];
			this.rBuffer[--charPos] = EventInterceptor.DIGIT_TENS[r];
		}
		// Fall thru to fast mode for smaller numbers
		// assert(i2 <= 65536, i2);
		for (;;) {
			q2 = i2 * 52429 >>> 16 + 3;
			r = i2 - q2 * 10;
			this.rBuffer[--charPos] = EventInterceptor.DIGITS[r];
			i2 = q2;
			if (i2 == 0) {
				break;
			}
		}
		if (sign != 0) {
			this.rBuffer[--charPos] = sign;
		}
		this.rBufferLength = spaceNeeded;
	}
	
	private void headAppend(final String s) {
		this.headAppend( ' ' );
		if (s == null) {
			this.headAppend( 'n' );
			this.headAppend( 'u' );
			this.headAppend( 'l' );
			this.headAppend( 'l' );
			return;
		}
		this.headAppend( '"' );
		for (int i = 0, l = s.length(); l > 0; ++i, --l) {
			final char c = s.charAt( i );
			switch (c) {
			case '\n':
				this.headAppend( '\r' );
				this.headAppend( '\n' );
				this.headAppend( '\t' );
				continue;
			case '"':
				this.headAppend( '\\' );
				break;
			}
			this.headAppend( c );
		}
		this.headAppend( '"' );
	}
	
	private final void headAppendCRLF() {
		final int newCount = this.rBufferLength + 2;
		if (newCount > this.rBuffer.length) {
			this.headExpand( newCount );
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
		System.arraycopy( this.rBuffer, 0, newValue, 0, this.rBufferLength );
		this.rBuffer = newValue;
	}
	
	@Override
	public final boolean main() throws Throwable {
		/**
		 * wait for the first event
		 */
		this.queue.switchPlanesWaitReady( 0L );
		
		this.stsLoops++;
		for (int loops = 256; loops > 0; loops--) {
			/**
			 * destroy will notify
			 */
			final FifoQueueMultithreadEnqueue<Event> queue = this.queue.switchPlanesWait( 0L );
			if (queue == null) {
				return !this.destroyed;
			}
			synchronized (EventInterceptor.targets) {
				for (Event next; (next = queue.pollFirst()) != null;) {
					this.processEvent( next );
				}
			}
		}
		return !this.destroyed;
	}
	
	@Override
	protected void onEvent(final Event event) {
		this.queue.offerLast( event );
	}
	
	void processEvent(final Event event) throws Throwable {
		this.rBufferLength = 0;
		{
			long diff = (event.getDate() - Engine.STARTED) / 10L;
			for (int l = 8; l >= 0; l--) {
				if (l == 6) {
					this.rBuffer[l] = '.';
					continue;
				}
				this.rBuffer[l] = (byte) ((int) (diff % 10L) + '0');
				diff /= 10L;
			}
			this.rBufferLength = 9;
		}
		{
			this.headAppend( ' ' );
			this.headAppend( '[' );
			this.headAppend( event.getProcess() );
			this.headAppend( ']' );
		}
		{
			this.headAppend( event.getEventTypeId() );
			this.headAppend( event.getTitle() );
			this.headAppend( event.getSubject() );
			this.headAppendCRLF();
		}
		{
			final byte[] bytes = new byte[this.rBufferLength];
			System.arraycopy( this.rBuffer, 0, bytes, 0, this.rBufferLength );
			for (final ObjectTarget<byte[]> target : EventInterceptor.targets) {
				target.absorb( bytes );
			}
		}
	}
	
	@Override
	public final boolean start() {
		this.destroyed = false;
		return true;
	}
	
	@Override
	public void statusFill(final StatusInfo data) {
		super.statusFill( data );
		data.put( "Handling Loops", this.stsLoops );
		data.put( "Run Exceptions", this.stsUnhandled );
	}
	
	@Override
	public final boolean stop() {
		this.destroyed = true;
		this.queue.switchQueueWaitCancel();
		return false;
	}
	
	@Override
	public final boolean unhandledException(final Throwable t) {
		this.stsUnhandled++;
		Report.exception( "EVENT-INTERCEPTOR", "Unhandled exception in a main loop", t );
		synchronized (this) {
			try {
				// sleep
				Thread.sleep( 99L );
				// wait for incoming
				this.wait( 399L );
			} catch (final InterruptedException e) {
				return false;
			}
		}
		return !this.destroyed;
	}
}
