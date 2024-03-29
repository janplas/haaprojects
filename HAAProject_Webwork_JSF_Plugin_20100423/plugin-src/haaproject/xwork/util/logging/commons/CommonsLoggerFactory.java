/*
 * Copyright (c) 2002-2006 by OpenSymphony
 * All rights reserved.
 */
package haaproject.xwork.util.logging.commons;

import haaproject.xwork.util.logging.Logger;
import haaproject.xwork.util.logging.LoggerFactory;

import org.apache.commons.logging.LogFactory;

/**
 * Creates commons-logging-backed loggers
 */
public class CommonsLoggerFactory extends LoggerFactory {

	@Override
	protected Logger getLoggerImpl(Class<?> cls) {
		return new CommonsLogger(LogFactory.getLog(cls));
	}

	@Override
	protected Logger getLoggerImpl(String name) {
		return new CommonsLogger(LogFactory.getLog(name));
	}

}
