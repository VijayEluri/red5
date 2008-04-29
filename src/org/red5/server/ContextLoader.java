package org.red5.server;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.management.ObjectName;

import org.red5.server.jmx.JMXAgent;
import org.red5.server.jmx.JMXFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.Resource;

/**
 * Red5 applications loader
 */
public class ContextLoader implements ApplicationContextAware, ContextLoaderMBean {
	/**
	 * Logger
	 */
	protected static Logger log = LoggerFactory.getLogger(ContextLoader.class);

	/**
	 * Spring Application context
	 */
	protected ApplicationContext applicationContext;

	/**
	 * Spring parent app context
	 */
	protected ApplicationContext parentContext;

	/**
	 * Context location files
	 */
	protected String contextsConfig;
	
	/**
	 * MBean object name used for de/registration purposes.
	 */
	private ObjectName oName;	

	/**
	 * Context map
	 */
	protected ConcurrentMap<String, ApplicationContext> contextMap = new ConcurrentHashMap<String, ApplicationContext>();

	/**
	 * @param applicationContext
	 *            Spring application context
	 * @throws BeansException
	 *             Top level exception for app context (that is, in fact, beans
	 *             factory)
	 */
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * Setter for parent application context
	 * 
	 * @param parentContext
	 *            Parent Spring application context
	 */
	public void setParentContext(ApplicationContext parentContext) {
		this.parentContext = parentContext;
	}

	/**
	 * Setter for context config name
	 * 
	 * @param contextsConfig
	 *            Context config name
	 */
	public void setContextsConfig(String contextsConfig) {
		this.contextsConfig = contextsConfig;
	}

	/**
	 * Loads context settings from ResourceBundle (.properties file)
	 * 
	 * @throws Exception
	 *             I/O exception, casting exception and others
	 */
	public void init() throws Exception {
		log.debug("ContextLoader init");
		// register in jmx
		//create a new mbean for this instance
		oName = JMXFactory.createObjectName("type", "ContextLoader");
		JMXAgent.registerMBean(this, this.getClass().getName(),	ContextLoaderMBean.class, oName);		
		
		// Load properties bundle
		Properties props = new Properties();
		Resource res = applicationContext.getResource(contextsConfig);
		if (!res.exists()) {
			log.error("Contexts config must be set.");
			return;
		}

		// Load properties file
		props.load(res.getInputStream());

		// Iterate thru properties keys and replace config attributes with
		// system attributes
		for (Object key : props.keySet()) {
			String name = (String) key;
			String config = props.getProperty(name);
			// TODO: we should support arbitrary property substitution
			config = config.replace("${red5.root}", System
					.getProperty("red5.root"));
			config = config.replace("${red5.config_root}", System
					.getProperty("red5.config_root"));
			log.info("Loading: {} = {}", name, config);

			// Load context
			loadContext(name, config);
		}

	}
	
	public void uninit() {
		log.debug("ContextLoader un-init");		
		JMXAgent.unregisterMBean(oName);
	}

	/**
	 * Loads a context (Red5 application) and stores it in a context map, then adds
	 * it's beans to parent (that is, Red5)
	 * 
	 * @param name
	 *            Context name
	 * @param config
	 *            Filename
	 */
	public void loadContext(String name, String config) {
		log.debug("Load context - name: {} config: {}", name, config);
		ApplicationContext context = new FileSystemXmlApplicationContext(
				new String[] { config }, parentContext);
		log.debug("Adding to context map - name: {} context: {}", name, context);
		contextMap.put(name, context);
		// add the context to the parent, this will be red5.xml
		ConfigurableBeanFactory factory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
		// Register context in parent bean factory
		log.debug("Registering - name: {}", name);
		factory.registerSingleton(name, context);
	}

	/**
	 * Unloads a context (Red5 application) and removes it from the context map, then removes
	 * it's beans from the parent (that is, Red5)
	 * 
	 * @param name
	 *            Context name
	 */	
	public void unloadContext(String name) {
		log.debug("Un-load context - name: {}", name);
		ApplicationContext context = contextMap.remove(name);
		log.debug("Context from map: {}", context);
		ConfigurableBeanFactory factory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
		if (factory.containsSingleton(name)) {
			log.debug("Context found in parent, destroying...");
			FileSystemXmlApplicationContext ctx = (FileSystemXmlApplicationContext) factory.getSingleton(name);
			if (ctx.isRunning()) {
				ctx.stop();
			}
			ctx.close();
			try {
				factory.destroyBean(name, ctx);
			} catch (Exception e) {
			}
		}
		context = null;
	}
	
	/**
	 * Return context by name
	 * 
	 * @param name
	 *            Context name
	 * @return Application context for given name
	 */
	public ApplicationContext getContext(String name) {
		return contextMap.get(name);
	}

	/**
	 * Return parent context
	 * 
	 * @return parent application context
	 */	
	public ApplicationContext getParentContext() {
		return parentContext;
	}

	public String getContextsConfig() {
		return contextsConfig;
	}
}
