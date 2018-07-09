package ru.myx.iface.logger;

import ru.myx.ae3.Engine;
import ru.myx.ae3.help.Format;
import ru.myx.ae3.report.EventInterceptor;
import ru.myx.ae3.status.StatusInfo;
import ru.myx.ae3.status.StatusProvider;

/*
 * Created on 20.12.2005
 */
/**
 * @author myx
 * 
 */
public final class LoggerStatusProvider implements StatusProvider {
	static int	stConnectionAttempts	= 0;
	
	static int	stConnectionsSuccess	= 0;
	
	@Override
	public String statusDescription() {
		return "DMESG watcher";
	}
	
	@Override
	public void statusFill(final StatusInfo data) {
		final int stConnectionsAttempts = LoggerStatusProvider.stConnectionAttempts;
		final int stConnectionsSuccess = LoggerStatusProvider.stConnectionsSuccess;
		final int stRequests = LoggerSocketHandler.stRequests;
		final int stBadRequests = LoggerSocketHandler.stBadRequests;
		final long started = Logger.STARTED;
		final long tt = Engine.fastTime() - started;
		final long tm = tt / 1000;
		final int stInlineParserCreations = LoggerHandlerQueue.stsInlineParserCreations;
		final int stUnexpectedFinalizations = LoggerSocketHandler.stUnexpectedFinalizations;
		data.put( "Connections attempts", Format.Compact.toDecimal( stConnectionsAttempts ) );
		data.put( "Connections success", Format.Compact.toDecimal( stConnectionsSuccess ) );
		data.put( "Conn. per second", Format.Compact.toDecimal( stConnectionsAttempts * 1.0 / tm ) );
		data.put( "Conn. per second LOGGER", Format.Compact.toDecimal( stConnectionsSuccess * 1.0 / tm ) );
		data.put( "Requests", Format.Compact.toDecimal( stRequests ) );
		data.put( "Bad requests", Format.Compact.toDecimal( stBadRequests ) );
		data.put( "Inline parser creations", Format.Compact.toDecimal( stInlineParserCreations ) );
		data.put( "Unexpected finalizations", Format.Compact.toDecimal( stUnexpectedFinalizations ) );
		data.put( "Reader initial buffer", Format.Compact.toBytes( LoggerSocketHandler.QBUFF_INIT ) );
		data.put( "Reader buffer step", Format.Compact.toBytes( LoggerSocketHandler.QBUFF_STEP ) );
		data.put( "Reader buffer expands", Format.Compact.toDecimal( LoggerSocketHandler.stExpands ) );
		EventInterceptor.statusFillStatic( data );
		data.put( "Serving time", Format.Compact.toPeriod( tt ) );
	}
	
	@Override
	public String statusName() {
		return "dmesg";
	}
}
