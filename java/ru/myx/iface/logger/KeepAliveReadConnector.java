/*
 * Created on 19.04.2006
 */
package ru.myx.iface.logger;

import java.util.function.Function;

final class KeepAliveReadConnector implements Function<LoggerSocketHandler, Object> {
	
	@Override
	public final Object apply(final LoggerSocketHandler parser) {
		parser.reconnect();
		return null;
	}
	
	@Override
	public final String toString() {
		return "KEEP-ALIVE RECONNECTOR";
	}
}
