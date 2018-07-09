/*
 * Created on 28.04.2006
 */
package ru.myx.iface.logger;

import ru.myx.ae3.binary.TransferSocket;

final class LoggerHandlerQueue {
	private static final int					LEAF_SIZE					= 32;
	
	private static final int					QUEUE_COUNT					= 32;
	
	private static final int					QUEUE_MASK					= LoggerHandlerQueue.QUEUE_COUNT - 1;
	
	private final int							queueIndex;
	
	private final LoggerSocketHandler[]			parsers;
	
	private int									count;
	
	static int									stsInlineParserCreations	= 0;
	
	private static final LoggerHandlerQueue[]	QUEUES						= LoggerHandlerQueue.createQueues();
	
	private static int							counter						= 0;
	
	private static final LoggerHandlerQueue[] createQueues() {
		final LoggerHandlerQueue[] queues = new LoggerHandlerQueue[LoggerHandlerQueue.QUEUE_COUNT];
		for (int i = LoggerHandlerQueue.QUEUE_MASK; i >= 0; --i) {
			queues[i] = new LoggerHandlerQueue( i );
		}
		return queues;
	}
	
	static final LoggerSocketHandler getParser(final TransferSocket socket) {
		final LoggerHandlerQueue queue = LoggerHandlerQueue.QUEUES[--LoggerHandlerQueue.counter
				& LoggerHandlerQueue.QUEUE_MASK];
		return queue.getParserImpl( socket );
	}
	
	static final void reuseParser(final LoggerSocketHandler parser, final int queueIndex) {
		LoggerHandlerQueue.QUEUES[queueIndex].reuseParser( parser );
	}
	
	private LoggerHandlerQueue(final int queueIndex) {
		this.queueIndex = queueIndex;
		this.parsers = new LoggerSocketHandler[LoggerHandlerQueue.LEAF_SIZE];
		for (int i = LoggerHandlerQueue.LEAF_SIZE - 1; i >= 0; --i) {
			this.parsers[i] = new LoggerSocketHandler( queueIndex );
			this.count++;
		}
	}
	
	private final LoggerSocketHandler getParserImpl(final TransferSocket socket) {
		LoggerSocketHandler parser;
		synchronized (this) {
			if (this.count > 0) {
				parser = this.parsers[--this.count];
				this.parsers[this.count] = null;
			} else {
				parser = null;
			}
		}
		if (parser == null) {
			LoggerHandlerQueue.stsInlineParserCreations++;
			parser = new LoggerSocketHandler( this.queueIndex );
		}
		parser.prepare( socket );
		return parser;
	}
	
	private final void reuseParser(final LoggerSocketHandler parser) {
		synchronized (this) {
			if (this.count < LoggerHandlerQueue.LEAF_SIZE) {
				this.parsers[this.count++] = parser;
			}
		}
	}
}
