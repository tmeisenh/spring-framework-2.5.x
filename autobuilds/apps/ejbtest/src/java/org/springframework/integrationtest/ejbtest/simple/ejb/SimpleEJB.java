/*
 * Copyright 2002-2004 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.springframework.integrationtest.ejbtest.simple.ejb;

import javax.ejb.CreateException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import net.sf.hibernate.SessionFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.access.ContextSingletonBeanFactoryLocator;
import org.springframework.ejb.support.AbstractStatelessSessionBean;
import org.springframework.integrationtest.ejbtest.Constants;

/**
 * <p>Simple test EJB.</p>
 *
 * <p>Note for XDoclet users. XDoclet is not smart enough to see that this class's
 * superclass implements SessionBean. If you are using XDoclet (not the case here,
 * this class would also have to directly implement SessionBean or XDoclet will
 * complain! Additionally, XDoclet is not smart enough to see the superclass
 * implements ejbCreate, so it will create its own empty ejbCreate method in the
 * final EJB class it generates. This will override the one from the superclass,
 * which means the superclass one will never get called, and the bean factory will
 * never get loaded. Bad XDoclet! The solution is to yourself add an ejbCreate
 * method (as per the example below) which just delegates to the superclass one,
 * and then XDoclet will not generate its own.</p>
 * 
 * @author colin sampaleanu
 * @version $Id: SimpleEJB.java,v 1.2 2004-07-12 21:38:16 colins Exp $
 */
public class SimpleEJB extends AbstractStatelessSessionBean
		implements SimpleService {

	// --- statics
	public static final String SESSION_FACTORY_ID = "sessionFactory";

	protected static final Log logger = LogFactory
			.getLog(SimpleEJB.class);

	// --- attributes
	SessionFactory sessionFactory;

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.ejb.SessionBean#setSessionContext(javax.ejb.SessionContext)
	 */
	public void setSessionContext(SessionContext sessionContext) {
		super.setSessionContext(sessionContext);
		setBeanFactoryLocator(ContextSingletonBeanFactoryLocator.getInstance());
		setBeanFactoryLocatorKey(Constants.SERVICE_LAYER_CONTEXT_ID);
	}

	/*
	 * arghhh! stupid method needed just to make XDoclet happy, otherwise it will
	 * create one in the subclass it generates, killing the one in the superclass
	 * @ejb.create-method
	 */
	public void ejbCreate() throws CreateException {
		super.ejbCreate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.ejb.support.AbstractStatelessSessionBean#onEjbCreate()
	 */
	protected void onEjbCreate() throws CreateException {
		sessionFactory = (SessionFactory) getBeanFactory().getBean(
				SESSION_FACTORY_ID);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.integrationtest.ejbtest.hibernate.tx.CmtJtaNoSpringTx#testMethod(java.lang.String)
	 */
	public String echo(String input) {
		return "hello " + input;
	}

}