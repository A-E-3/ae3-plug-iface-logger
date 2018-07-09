package ru.myx.iface.logger;
import ru.myx.ae3.produce.Produce;
import ru.myx.ae3.status.StatusRegistry;

/**
 * @author myx
 * 
 */
public final class Main {
	
	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		System.out.println( "BOOT: LOGGER is being initialized..." );
		StatusRegistry.ROOT_REGISTRY.register( new LoggerStatusProvider() );
		Produce.registerFactory( new LoggerTargetFactory() );
		System.out.println( "BOOT: LOGGER OK" );
	}
	
}
