package ru.myx.iface.logger;

import java.util.function.Function;
import ru.myx.ae3.base.BaseObject;
import ru.myx.ae3.binary.TransferSocket;
import ru.myx.ae3.flow.ObjectTarget;
import ru.myx.ae3.produce.ObjectFactory;

/*
 * Created on 09.02.2005
 */
/**
 * @author myx
 *
 */
public final class LoggerTargetFactory implements ObjectFactory<Object, ObjectTarget<TransferSocket>> {

	private static final Class<?>[] TARGETS = {
			ObjectTarget.class, Function.class
	};

	private static final Class<?>[] SOURCES = null;

	private static final String[] VARIETY = {
			"dmesg_parser", Logger.PNAME_DMESG
	};

	@Override
	public final boolean accepts(final String variant, final BaseObject attributes, final Class<?> source) {

		return true;
	}

	@Override
	public final ObjectTarget<TransferSocket> produce(final String variant, final BaseObject attributes, final Object object) {

		return new LoggerSocketTarget();
	}

	@Override
	public final Class<?>[] sources() {

		return LoggerTargetFactory.SOURCES;
	}

	@Override
	public final Class<?>[] targets() {

		return LoggerTargetFactory.TARGETS;
	}

	@Override
	public final String[] variety() {

		return LoggerTargetFactory.VARIETY;
	}
}
