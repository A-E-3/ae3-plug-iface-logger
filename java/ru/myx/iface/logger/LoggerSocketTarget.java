package ru.myx.iface.logger;

import ru.myx.ae3.binary.TransferSocket;
import ru.myx.ae3.binary.TransferTarget;
import ru.myx.ae3.flow.ObjectTarget;
import ru.myx.ae3.report.Report;

/*
 * Created on 30.11.2005

 */
final class LoggerSocketTarget implements ObjectTarget<TransferSocket> {
	
	private static final String OWNER = "DMESG_TARGET";

	LoggerSocketTarget() {
		//
	}

	@Override
	public final boolean absorb(final TransferSocket socket) {
		
		LoggerStatusProvider.stConnectionAttempts++;
		final TransferTarget parser = LoggerHandlerQueue.getParser(socket);
		if (Report.MODE_DEBUG) {
			Logger.LOG.event(LoggerSocketTarget.OWNER, "CONNECTING", "socket=" + socket.getIdentity() + ", parser=" + parser);
		}
		final boolean result = socket.getSource().connectTarget(parser);
		if (result) {
			LoggerStatusProvider.stConnectionsSuccess++;
		}
		return result;
	}

	@Override
	public final Class<? extends TransferSocket> accepts() {
		
		return TransferSocket.class;
	}

	@Override
	public final String toString() {
		
		return LoggerSocketTarget.OWNER;
	}
}
