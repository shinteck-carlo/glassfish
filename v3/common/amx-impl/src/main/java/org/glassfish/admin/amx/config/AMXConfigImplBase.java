/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.admin.amx.config;

import com.sun.appserv.management.base.AMX;
import com.sun.appserv.management.base.Container;
import com.sun.appserv.management.base.Util;
import com.sun.appserv.management.base.XTypes;
import com.sun.appserv.management.config.*;
import com.sun.appserv.management.helper.RefHelper;
import com.sun.appserv.management.util.jmx.JMXUtil;
import com.sun.appserv.management.util.misc.*;
import org.glassfish.admin.amx.dotted.DottedName;
import org.glassfish.admin.amx.mbean.AMXImplBase;
import org.glassfish.admin.amx.mbean.ContainerSupport;
import org.glassfish.admin.amx.mbean.Delegate;
import org.glassfish.admin.amx.util.AMXConfigInfoResolver;
import org.glassfish.admin.amx.util.SingletonEnforcer;
import org.glassfish.admin.amx.util.UnregistrationListener;
import org.jvnet.hk2.config.ConfigBean;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import javax.management.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
	Base class from which all AMX Config MBeans should derive (but not "must").
	<p>
 */
public class AMXConfigImplBase extends AMXImplBase
	implements AMXConfig, DefaultValues  // and others more conveniently implemented generically
{
    private final Class<?> mSupplementaryInterface;
    
		public
	AMXConfigImplBase( 
        final String        j2eeType,
        final String        fullType,
        final ObjectName    parentObjectName,
		final Class<? extends AMX> theInterface,
        final  Class<?>     supplementaryInterface,
		final Delegate		delegate )
	{
		super( j2eeType, fullType, parentObjectName, theInterface, delegate );
        
        mSupplementaryInterface = supplementaryInterface;
	}
    
        private String
    getTypeString()
    {
        // use the serverbeans type for now, later extract the XML element type
        final ConfigBean cb = getConfigBean();
        final Class<? extends ConfigBeanProxy> intf = cb.getProxyType();
        final Package pkg = intf.getPackage();
        String result = intf.getName().substring( pkg.getName().length() + 1, intf.getName().length() );

        return result;
    }
    
    /**
        The actual name could be different than the 'name' property in the ObjectName if it
        contains characters that are illegal for an ObjectName.
     */
    @Override
		public String
	getName()
	{
        final ConfigBean cb = getConfigBean();
        
        return AMXConfigLoader.getName( cb, null );
	}
      
    @Override
    /**
        By default take a camel-case name and insert dashes
     */
        protected String
    _getDottedNamePart()
    {
        String result = super._getDottedNamePart();
        
        if ( isSingletonMBean( getInterface() ) )
        {
            result = DottedName.hyphenate(getTypeString());
        }
        else
        {
            final Container container = getContainer();
            if ( container instanceof ConfigCollectionElement )
            {
                // an intermediate MBean is already in place (self), providing a grouping
                result = getName();
            }
            else
            {
                // we need to insert the type of the element to group them uniquely
                result = DottedName.hyphenate(getTypeString()) + ":" + getName();
            }
        }
        return result;
    }


        public Set<String>
    getContaineeJ2EETypes()
    {
        final Set<String> j2eeTypes = super.getContaineeJ2EETypes();
                
        final ContainedTypeInfo info = new ContainedTypeInfo( getConfigBean() );
        j2eeTypes.addAll( info.findAllContainedJ2EETypes().keySet() );
        
        return j2eeTypes;
    }


	    protected boolean
	supportsProperties()
	{
	    return PropertiesAccess.class.isAssignableFrom( getInterface() );
	}
	
	    protected boolean
	supportsSystemProperties()
	{
	    return SystemPropertiesAccess.class.isAssignableFrom( getInterface() );
	}
	
	@Override
		protected final Set<String>
	getSuperfluousMethods()
	{
	    final Set<String>   items   = super.getSuperfluousMethods();
	    
	    final Method[]  methods = this.getClass().getMethods();
	    for( final Method m : methods )
	    {
	        final String    name    = m.getName();
	        
	        if (   isRemoveConfig( name ) ||
	                isCreateConfig( name ) )
	        {
	            if ( m.getParameterTypes().length <= 1 )
	            {
	                items.add( name );
	            }
	        }
	    }
	    
	    return items;
	}
	
		private static void
	validatePropertyName( final String propertyName )
	{
		if ( propertyName == null ||
			propertyName.length() == 0 )
		{
			throw new IllegalArgumentException( "Illegal property name: " +
				StringUtil.quote( propertyName ) );
		}
	}
    
    
    	protected final <T extends AnyPropertyConfig> Map<String,String>
	asNameValuePairs( final Map<String,T> items )
	{
        final Map<String,String>  result = new HashMap<String,String>();
        for( final String name : items.keySet() )
        {
            final AnyPropertyConfig any = items.get(name);
            final String value = any.getValue();
            result.put( name, value );
        }
        
        return result;
    }
    
		protected final <T extends AnyPropertyConfig>  AnyPropertyConfig
	getAnyPropertyConfig( Map<String,T> props, final String propertyName )
	{
        return props.get(propertyName);
	}
    
		protected final <T extends AnyPropertyConfig>  String
	getPropertyValue( Map<String,T> props, final String propertyName )
	{
        final AnyPropertyConfig prop = props.get(propertyName);
        return prop == null ? null : prop.getValue();
	}
    
        private void
    validateNameValue( final String propertyName, final String propertyValue )
    {
		validatePropertyName( propertyName );
		if ( propertyValue == null  )
		{
			throw new IllegalArgumentException( "null" );
		}
    }
    
        private final DelegateToConfigBeanDelegate
    getConfigDelegate()
    {
        return DelegateToConfigBeanDelegate.class.cast( getDelegate() );
    }
    
    
        private final ConfigBean
    getConfigBean()
    {
        return getConfigDelegate().getConfigBean();
    }


//========================================================================================
    protected Map<String,PropertyConfig> getPropertyConfigMap() { return getSelf(PropertiesAccess.class).getPropertyConfigMap(); }
    
    	public Map<String,String>
	getProperties( )
	{
        return asNameValuePairs( getPropertyConfigMap() );
	}
	
		public String[]
	getPropertyNames( )
	{
		return( GSetUtil.toStringArray( getPropertyConfigMap().keySet() ) );
	}
	
		public String
	getPropertyValue( String propertyName )
	{   
        return getPropertyValue( getPropertyConfigMap(), propertyName );
    }
	
		public final void
	setPropertyValue(
		final String propertyName,
		final String propertyValue )
	{
		validateNameValue( propertyName, propertyValue );
        
        final PropertyConfig prop =  getPropertyConfigMap().get( propertyName );
        if ( prop != null )
        {
            prop.setValue( propertyValue );
        }
        else
        {
            createProperty( propertyName, propertyValue );
        }
	}
	
		public final boolean
	existsProperty( final String propertyName )
	{
		return getPropertyConfigMap().keySet().contains( propertyName );
	}
	
		public final void
	removeProperty( final String propertyName )
	{
        // reinvoke with non-deprecated auto-generic impl
        getSelf( PropertiesAccess.class ).removePropertyConfig( propertyName );
	}
	
		public final void
	createProperty( final String propertyName, final String propertyValue )
	{
        // reinvoke with non-deprecated auto-generic impl
        getSelf( PropertiesAccess.class ).createPropertyConfig( propertyName, propertyValue );
	}

		public final String
	getGroup()
	{
		return( AMX.GROUP_CONFIGURATION );
	}
	
//========================================================================================
    protected Map<String,SystemPropertyConfig> getSystemPropertyConfigMap() { return getSelf(SystemPropertiesAccess.class).getSystemPropertyConfigMap(); }

		public Map<String,String>
	getSystemProperties( )
	{
        return asNameValuePairs( getSystemPropertyConfigMap() );
	}
	
		public String[]
	getSystemPropertyNames( )
	{
		return( GSetUtil.toStringArray( getSystemPropertyConfigMap().keySet() ) );
	}
	
		public String
	getSystemPropertyValue( final String propertyName )
	{
        return getPropertyValue( getSystemPropertyConfigMap(), propertyName );
	}
	
		public final void
	setSystemPropertyValue(
		final String propertyName,
		final String propertyValue )
	{
		validateNameValue( propertyName, propertyValue );
        
        final SystemPropertyConfig prop =  getSystemPropertyConfigMap().get( propertyName );
        if ( prop != null )
        {
            prop.setValue( propertyValue );
        }
        else
        {
            createSystemProperty( propertyName, propertyValue );
        }
	}
	
		public final boolean
	existsSystemProperty( final String propertyName )
	{
		return getSystemPropertyConfigMap().keySet().contains( propertyName );
	}
	
		public final void
	removeSystemProperty( String propertyName )
	{
        // reinvoke with non-deprecated auto-generic impl
        getSelf( SystemPropertiesAccess.class ).removeSystemPropertyConfig( propertyName );
	}
	
		public final void
	createSystemProperty( String propertyName, String propertyValue )
	{
        // reinvoke with non-deprecated auto-generic impl
        getSelf( SystemPropertiesAccess.class ).createSystemPropertyConfig( propertyName, propertyValue );
	}
	
//========================================================================================
    
	    public MBeanNotificationInfo[]
	getNotificationInfo()
	{
	    final MBeanNotificationInfo[]   superInfos = super.getNotificationInfo();
	    
		// create a NotificationInfo for AttributeChangeNotification
		final String description	= "";
		final String[]	notifTypes	= new String[] { AttributeChangeNotification.ATTRIBUTE_CHANGE };
		final MBeanNotificationInfo	attributeChange = new MBeanNotificationInfo(
				notifTypes,
				AttributeChangeNotification.class.getName(),
				description );
	
		final MBeanNotificationInfo[]	selfInfos	=
			new MBeanNotificationInfo[]	{ attributeChange };

		final MBeanNotificationInfo[]	allInfos	=
			JMXUtil.mergeMBeanNotificationInfos( superInfos, selfInfos );
			
	    return allInfos;
	}
	
    /*
	    private String
	getSimpleInterfaceName( final AMX amx )
    {
        final String fullInterfaceName  = Util.getExtra( amx ).getInterfaceName();
        final String interfaceName   = ClassUtil.stripPackageName( fullInterfaceName );
        
        return interfaceName;
    }
    */
    
    
	/**
	    Do anything necessary prior to removing an AMXConfig.
	    <p>
	    We have the situation where some of the com.sun.appserv MBeans
	    behave by auto-creating references, even in EE, but not auto-removing,
	    though in PE they are always auto-removed.  So the algorithm varies
	    by both release (PE vs EE) and by MBean.  This is hopeless.
	    <p>
	    So first we attempt remove all references to the AMXCOnfig (if any).
	    This will fail in PE, and may or may not fail in EE; we just ignore
	    it so long as there is only one failure.
	 */
	    protected void
    preRemove( final ObjectName objectName )
    {
        final AMXConfig amxConfig = getProxy( objectName, AMXConfig.class );
        
        if ( amxConfig instanceof RefConfigReferent )
        {
            debug( "*** Removing all references to ", objectName );
            
            final Set<RefConfig>  failures    =
                RefHelper.removeAllRefsTo( (RefConfigReferent)amxConfig, true );
            if( failures.size() != 0 )
            {
                debug( "FAILURE removing references to " + objectName  + ": " +
                    CollectionUtil.toString( Util.toObjectNames( failures ) ) );
            }
	    }
	    else
	    {
            debug( "*** not a RefConfigReferent: ", objectName );
	    }
    }
    
	/**
	    Make sure the item exists, then call preRemove( ObjectName ).
	 */
		protected ObjectName
    preRemove(
        final Map<String,ObjectName>    items,
        final String                    name )
    {
        if ( name == null )
        {
            throw new IllegalArgumentException( "null name" );
        }

        final ObjectName    objectName  = items.get( name );
        if ( objectName == null )
        {
            throw new IllegalArgumentException( "Item not found: " + name );
        }
        
        preRemove( objectName );
        
        return objectName;
    }
  
    
     
    
    static private final String CREATE = "create";
    static private final String CREATE_PREFIX  = CREATE;
    static private final String REMOVE_PREFIX  = "remove";
    static private final String CONFIG_SUFFIX  = "Config";
    static private final String FACTORY_SUFFIX  = "Factory";
    
    static private final Class[]   STRING_SIG  = new Class[] { String.class };
    
    /**
        Generic removal of RefConfig.
        protected void
    removeRefConfig( final String j2eeType, final String name )
    {
        removeConfig( j2eeType, name );
    }
     */
    
    
        private boolean
    isRemoveConfig( final String operationName)
    {
        return operationName.startsWith( REMOVE_PREFIX ) &&
	        operationName.endsWith( CONFIG_SUFFIX );
    }
    
        private boolean
    isRemoveConfig(
		String 		operationName,
		Object[]	args,
		String[]	types )
    {
        final int   numArgs = args == null ? 0 : args.length;
        
        boolean isRemove    = numArgs <= 1 && isRemoveConfig( operationName );
        if ( isRemove && numArgs == 1 )
        {
            isRemove    = types[0].equals( String.class.getName() );
        }
        return isRemove;
    }
   
        private boolean
    isCreateConfig( final String operationName)
    {
        return operationName.startsWith( CREATE_PREFIX ) &&
	        operationName.endsWith( CONFIG_SUFFIX );
    }
    
        private boolean
    isGenericCreateConfig( final String operationName)
    {
        // eg "createConfig"
        return operationName.equals( CREATE_PREFIX + CONFIG_SUFFIX);
    }
     
        private Method
    getCreateMethod(
        final String    operationName,
        final String[]  types )
        throws ClassNotFoundException
    {
        final Class[] signature = ClassUtil.signatureFromClassnames(types);
        
        final Class<? extends AMXConfig> myInterface = getInterface();
        final Method m = ClassUtil.findMethod( myInterface, operationName, signature );
        
        if ( m == null )
        {
            throw new IllegalArgumentException( "Can't find method " + operationName );
        }
        
        if ( ! AMXConfig.class.isAssignableFrom(  m.getReturnType() ) )
        {
            throw new IllegalArgumentException( "Class " + m.getReturnType().getName() + " is not a subclass of AMXConfig" );
        }
        
        return m;
    }
        
    /*
        protected ObjectName
   createAMXConfig(
        final String         j2eeType,
        Map<String,Object>   params )
        throws ClassNotFoundException, TransactionFailure
   {
        final Class<? extends AMXConfig>  returnType = null;
        final AMXCreateInfo amxCreateInfo = returnType.getAnnotation( AMXCreateInfo.class );
        if ( amxCreateInfo == null )
        {
            // might or might not be a problem
        }
        
        final Map<String,String> properties = extractProperties( params, PropertiesAccess.PROPERTY_PREFIX);
        final Map<String,String> systemProperties = extractProperties( params, SystemPropertiesAccess.SYSTEM_PROPERTY_PREFIX);
        
        final String[] paramNames = amxCreateInfo.paramNames();
        cdebug( "createConfig:  paramNames = {" + StringUtil.toString(paramNames) + "}" );
        rejectBadAttrs( params );

        final ContainedTypeInfo   subInfo = new ContainedTypeInfo( getConfigBean() );
        final Class<? extends ConfigBeanProxy>  newItemClass = subInfo.getConfigBeanProxyClassFor(j2eeType);
        if ( newItemClass == null )
        {
            throw new IllegalArgumentException( "Can't find class for j2eeType " + j2eeType );
        }
        final AMXConfigInfoResolver resolver = subInfo.getAMXConfigInfoResolverFor( j2eeType );
        
        // check for illegal use of properties on configs that don't have them
        if ( properties.keySet().size() != 0 && ! resolver.supportsProperties() )
        {
            throw new IllegalArgumentException(
                "Properties specified, but not supported by " + resolver.amxInterface().getName() );
        }
        // check for illegal use of system properties on configs that don't have them
        if ( systemProperties.keySet().size() != 0 && ! resolver.supportsSystemProperties()  )
        {
            throw new IllegalArgumentException(
                "Properties specified, but not supported by " + resolver.amxInterface().getName() );
        }

        ConfigBean newConfigBean = null;
        try
        {
            newConfigBean = ConfigSupport.createAndSet( getConfigBean(), newItemClass, params);
        }
        catch( Throwable t )
        {
            cdebug( ExceptionUtil.toString(t) );
        }

        final AMXConfigLoader  amxLoader = SingletonEnforcer.get( AMXConfigLoader.class );
        amxLoader.handleConfigBean( newConfigBean, true );
            
        final ObjectName objectName = newConfigBean.getObjectName();
       // sendConfigCreatedNotification( objectName );
    cdebug( "NEW OBJECTNAME:  " + objectName);
       
       *
        // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        if ( resolver.supportsProperties() && properties.keySet().size() == 0 )
        {
            properties.put( "test1", "value1" ); // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            properties.put( "test2", "value2" ); // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            properties.put( "test3", "value3" ); // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        }
        // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        *
        
        final AMXConfig newAMX = AMXConfig.class.cast( getProxyFactory().getProxy( objectName ) );
        setAllProperties( newAMX, properties, systemProperties );
    
        return objectName;

   }
   */

        private void
    checkPropertiesOK(
        final Class<? extends AMXConfig>  intf,
        final ConfigCreateArgSupport      argSpt )
    {
        // check for illegal use of properties on configs that don't have them
        if ( argSpt.getProperties().keySet().size() != 0 &&
            ! PropertiesAccess.class.isAssignableFrom( intf ) )
        {
            throw new IllegalArgumentException(
                "Properties specified, but not supported by " + intf.getName() );
        }
        // check for illegal use of system properties on configs that don't have them
        if ( argSpt.getSystemProperties().keySet().size() != 0 &&
            ! SystemPropertiesAccess.class.isAssignableFrom( intf ) )
        {
            throw new IllegalArgumentException(
                "Properties specified, but not supported by " + intf.getName() );
        }
    }
    
        private AMXCreateInfo
    getAMXCreateInfo(
        final Method m,
        final Class<? extends AMXConfig> intf,
        final int numArgs )
    {     
        // check the method first, then the interface.
        AMXCreateInfo amxCreateInfo = m.getAnnotation( AMXCreateInfo.class );
        if ( amxCreateInfo == null )
        {
            // if the Method has no AMXCreateInfo, accept the defaults from the Class
            amxCreateInfo = intf.getAnnotation( AMXCreateInfo.class );
            if ( amxCreateInfo == null )
            {
                cdebug( "No AMXCreateInfo found for interface " + intf.getName() );
            }
        }
        
        // if not on the method, check the interface
        if ( amxCreateInfo == null )
        {
            // this is OK if there are no ordered parameters eg no parameters or an optional map only
            if ( numArgs != 0 )
            {
                throw new IllegalArgumentException(
                    "Method " + m.getName() + " must be annotated with " + AMXCreateInfo.class.getName() );
            }
        }
        return amxCreateInfo;
    }
    
        protected ObjectName
   createConfig(
        final String operationName,
        final Object[] args,
        String[]	   types)
        throws ClassNotFoundException, TransactionFailure
   {
        if ( ! isCreateConfig( operationName ) )
        {
            throw new IllegalArgumentException( "Illegal method name for create: " + operationName );
        }
        ObjectName  result  = null;
        
        //
        // Parse out the arguments
        //
        final ConfigCreateArgSupport argSpt = new ConfigCreateArgSupport( operationName, args, types );
        
        final Method m = getCreateMethod( operationName, types );
        final Class<? extends AMXConfig> returnType = (Class<? extends AMXConfig>)m.getReturnType();
        checkPropertiesOK( returnType, argSpt );
        
        final String j2eeType = Util.getJ2EEType( returnType );
        cdebug( "createConfig: j2eeType = " + j2eeType + ", return type = " + returnType.getName() );
        // Verify that the j2eeType matches the type expected from the operation name
        final String altJ2EEType = XTypes.PREFIX + operationName.substring( CREATE_PREFIX.length(), operationName.length() );
        if ( ! j2eeType.equals(altJ2EEType) )
        {
            throw new IllegalArgumentException( "j2eeType " + j2eeType + " != " + altJ2EEType );
        }
                        
        final AMXCreateInfo amxCreateInfo = getAMXCreateInfo( m, returnType, argSpt.numArgs() );
        final String[] paramNames = amxCreateInfo.paramNames();
        cdebug( "createConfig:  paramNames = {" + StringUtil.toString(paramNames) + "}" );
        argSpt.addExplicitAttrs( paramNames );
    
        final ContainedTypeInfo   subInfo = new ContainedTypeInfo( getConfigBean() );
        final Class<? extends ConfigBeanProxy>  newItemClass = subInfo.getConfigBeanProxyClassFor(j2eeType);
        if ( newItemClass == null )
        {
            throw new IllegalArgumentException( "Can't find class for j2eeType " + j2eeType );
        }
        final AMXConfigInfoResolver resolver = subInfo.getAMXConfigInfoResolverFor( j2eeType );
        if ( resolver.amxInterface() != returnType )
        {
            throw new IllegalArgumentException();
        }
  
        cdebug( "calling ConfigSupport.createAndSet() " );
        ConfigBean newConfigBean = null;
        try
        {
            newConfigBean = ConfigSupport.createAndSet( getConfigBean(), newItemClass, argSpt.getAttrs() );
        }
        catch( Throwable t )
        {
            cdebug( ExceptionUtil.toString(t) );
            t.printStackTrace();
        }

        final AMXConfigLoader  amxLoader = SingletonEnforcer.get( AMXConfigLoader.class );
        amxLoader.handleConfigBean( newConfigBean, true );
            
        final ObjectName objectName = newConfigBean.getObjectName();
       // sendConfigCreatedNotification( objectName );
    cdebug( "NEW OBJECTNAME:  " + objectName);
       
       /*
        // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        if ( resolver.supportsProperties() && properties.keySet().size() == 0 )
        {
            properties.put( "test1", "value1" ); // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            properties.put( "test2", "value2" ); // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            properties.put( "test3", "value3" ); // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        }
        // TESTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        */
        
        final AMXConfig newAMX = AMXConfig.class.cast( getProxyFactory().getProxy( objectName ) );
        setAllProperties( newAMX, argSpt.getProperties(), argSpt.getSystemProperties() );
    
        return objectName;
   }

/*

This is no good--it introduces dependencies on specific types
    private static final class AddProperties extends ConfigSupport.TransactionCallBack<WriteableView>
    {
        private final Map<String,String> mProperties;
        private final Map<String,String> mSystemProperties;
        private final Class<? extends ConfigBeanProxy> mIntf;
        
        public AddProperties(
            final Class<? extends ConfigBeanProxy> intf,
            final Map<String,String> properties, 
            final Map<String,String> systemProperties )
        {
            mIntf = intf;
            mProperties = properties;
            mSystemProperties = systemProperties;
        }
    
       public void performOn(WriteableView view) throws TransactionFailure
       {
            if ( mProperties.size() != 0 )
            {
                try {
                    final Method m = param.getProxyType().getMethod( "getProperty");
                    final List<Property> props = TypeCast.asList( m.invoke( view.getProxy(mIntf)) );

                    for( final String key : mProperties )
                    {
                        final Property prop = view.allocateProxy(Property.class);
                        prop.setName( key );
                        prop.setValue( mProperties.get(key) );
                        props.add( prop );
                    }
                }
                catch(Exception e)
                {
                    throw new TransactionFailure("Cannot add property to listener", e);
                }
            }
            
            if ( mSystemProperties.size() != 0 )
            {
                try {
                    final Method m = param.getProxyType().getMethod( "getSystemProperty");
                    final List<SytemProperty> props = TypeCast.asList( m.invoke( view.getProxy(mIntf)) );

                    for( final String key : mSystemProperties )
                    {
                        final SystemProperty prop = view.allocateProxy(SystemProperty.class);
                        prop.setName( key );
                        prop.setValue( mSystemProperties.get(key) );
                        props.add( prop );
                    }
                }
                catch(Exception e)
                {
                    throw new TransactionFailure("Cannot add property to listener", e);
                }
            }
       }
    }
*/
    
    /**
        This should be done in one transaction...
     */
        private void
    setAllProperties(
        final AMXConfig amxConfig,
        final Map<String,String> properties, 
        final Map<String,String> systemProperties )
    {
        if ( properties.keySet().size() != 0 )
        {
            final PropertiesAccess pa = PropertiesAccess.class.cast( amxConfig );
            for( final String propName : properties.keySet() )
            {
                final String propValue = properties.get(propName);
                pa.createPropertyConfig( propName, propValue );
            }
        }
        
        if ( systemProperties.keySet().size() != 0 )
        {
            final SystemPropertiesAccess pa = SystemPropertiesAccess.class.cast( amxConfig );
            for( final String propName : systemProperties.keySet() )
            {
                final String propValue = systemProperties.get(propName);
                pa.createSystemPropertyConfig( propName, propValue );
            }
        }
    }
    
		protected boolean
	mySleep( final long millis )
	{
		boolean	interrupted	= false;
		
		try
		{
			Thread.sleep( millis );
		}
		catch( InterruptedException e )
		{
			Thread.interrupted();
			interrupted	= true;
		}
		
		return interrupted;
	}


    private static final Set<String> CR_PREFIXES =
        GSetUtil.newUnmodifiableStringSet(
            "create", "remove"
        );
        
  
     /**
        Return the simple (no package) classname associated
        with certain operations:
        <ul>
        <li>removeAbcConfig => AbcConfig</li>
        <li>createAbcConfig => AbcConfig</li>
        <li>getAbcConfig => AbcConfig</li>
        </ul>
    */
        protected String
	operationNameToSimpleClassname( final String operationName )
    {
        return StringUtil.findAndStripPrefix( CR_PREFIXES, operationName );
    }
    
	    protected String
	operationNameToJ2EEType( final String operationName )
    {
        String  j2eeType   = null;
        
        if ( isRemoveConfig( operationName ) ||
              isCreateConfig( operationName )  )
        {
            j2eeType   = XTypes.PREFIX + operationNameToSimpleClassname( operationName );
        }
        else
        {
            j2eeType    = super.operationNameToJ2EEType( operationName );
        }
        return j2eeType;
    }
            
    /**
        Generic removal of any config contained by this config.
     */
        public final void
    removeConfig( final ObjectName containeeObjectName )
    {
        final ContainerSupport containerSupport = getContainerSupport();

	    preRemove( containeeObjectName );
        
        final AMXConfigImplBase child = (AMXConfigImplBase)get__ObjectRef( containeeObjectName );
        try
        {
cdebug( "REMOVING config of class " + child.getConfigBean().getProxyType().getName() + " from  parent of type " + 
    getConfigBean().getProxyType().getName() + ", ObjectName = " + JMXUtil.toString(containeeObjectName) );
            ConfigSupport.deleteChild( getConfigBean(), child.getConfigBean() );
        }
        catch( final TransactionFailure tf )
        {
            throw new RuntimeException( "Transaction failure deleting " + JMXUtil.toString(containeeObjectName), tf );
        }

        // NOTE: MBeans unregistered asynchronously by AMXConfigLoader
        // enforce synchronous semantics to clients by waiting until this happens
        final UnregistrationListener myListener = new UnregistrationListener( getMBeanServer(), containeeObjectName);
        final long TIMEOUT_MILLIS = 10 * 1000;

        final boolean unregisteredOK = myListener.waitForUnregister( TIMEOUT_MILLIS );
        if ( ! unregisteredOK )
        {
            throw new RuntimeException( "Something went wrong unregistering MBean " + JMXUtil.toString(containeeObjectName) );
        }
        
        
        //sendConfigRemovedNotification( containeeObjectName );
    }

    /**
        Generic removal of any config contained by this config.
     */
        public final void
    removeConfig( final String j2eeType, final String name )
    {
        final ContainerSupport containerSupport = getContainerSupport();
        final ObjectName    containeeObjectName  = containerSupport.getContaineeObjectName( j2eeType, name);
        if ( containeeObjectName == null )
        {
            throw new RuntimeException( new InstanceNotFoundException( "No MBean named " + name + " of j2eeType " + j2eeType + " found." ) );
        }
        removeConfig( containeeObjectName );
    }


   /**
        Remove config for a singleton Containee.
    */
      protected void
   removeConfig( final String operationName)
   {
        final String        j2eeType    = operationNameToJ2EEType( operationName );
        final ContainerSupport containerSupport = getContainerSupport();
        final ObjectName    containeeObjectName  = containerSupport.getContaineeObjectName( j2eeType );
        if ( containeeObjectName == null )
        {
            throw new RuntimeException( new InstanceNotFoundException( j2eeType ) );
        }
        removeConfig( containeeObjectName );
   }
   
   /**
        Remove config for a named Containee.
    */
      protected void
   removeConfig(
        final String   operationName,
        final Object[] args,
        String[]	   types)
        throws InvocationTargetException
   {
        if ( args == null || args.length == 0 )
        {
cdebug( "removeConfig: by operation name only" );
            // remove a singleton
            removeConfig( operationName );
        }
        else if ( args.length == 1 )
        {
cdebug( "removeConfig: by operationName + name" );
            // remove by name, type is implicit in method name
            removeConfig( operationNameToJ2EEType(operationName), (String)args[0] );
        }
        else if ( args.length == 2 )
        {
cdebug( "removeConfig: by  j2eeType + name" );
            // generic form
            if ( ! operationName.equals( "removeConfig" ) )
            {
                throw new IllegalArgumentException();
            }
            removeConfig( (String)args[0], (String)args[1] );
        }
        else
        {
            throw new IllegalArgumentException();
        }
   }
      
    /**
        Automatically figure out get<abc>Factory(), 
        create<Abc>Config(), remove<Abc>Config().
        
     */
    	protected Object
	invokeManually(
		String 		operationName,
		Object[]	args,
		String[]	types )
		throws MBeanException, ReflectionException, NoSuchMethodException, AttributeNotFoundException
	{
	    final int   numArgs = args == null ? 0 : args.length;
	    
	    Object  result  = null;
	    debugMethod( operationName, args );

	    if ( isRemoveConfig( operationName, args, types ) )
	    {
	        try
	        {
                removeConfig( operationName, args, types );
	        }
	        catch( InvocationTargetException e )
	        {
	            throw new MBeanException( e );
	        }
	    }
	    else if ( isCreateConfig( operationName ) )
	    {
	        try
	        {
	            result  = createConfig( operationName, args, types);
	        }
	        catch( Exception e )
	        {
	            throw new MBeanException( e );
	        }
	    }
	    else
	    {
	        result  = super.invokeManually( operationName, args, types );
	    }
	    return result;
	}
	
	
	/**
		Get the name of the config in which this MBean lives.
		
		@return config name, or null if not in a config
	 */
		public String
	getConfigName()
	{
		return( (String)getKeyProperty( XTypes.CONFIG_CONFIG ) );
	}
	
		public void
	sendConfigCreatedNotification( final ObjectName configObjectName )
	{
		sendNotification( AMXConfig.CONFIG_CREATED_NOTIFICATION_TYPE,
		    AMXConfig.CONFIG_REMOVED_NOTIFICATION_TYPE,
			AMXConfig.CONFIG_OBJECT_NAME_KEY, configObjectName );
	}
	
		public void
	sendConfigRemovedNotification( final ObjectName configObjectName )
	{
		sendNotification( AMXConfig.CONFIG_REMOVED_NOTIFICATION_TYPE,
		    AMXConfig.CONFIG_REMOVED_NOTIFICATION_TYPE,
			AMXConfig.CONFIG_OBJECT_NAME_KEY, configObjectName );
	}
    
    
    public String getDefaultValue( final String amxName )
    {
        final Class<? extends ConfigBeanProxy> myIntf = getConfigBean().getProxyType();
        
        final Map<String,String> defaultValues = _getDefaultValues( myIntf );
        
        final String xmlName = NameMapping.getInstance(getJ2EEType()).getXMLName( amxName );
        return defaultValues.get( xmlName );
    }
    
    
        final Map<String,String>
    _getDefaultValues( final Class<? extends ConfigBeanProxy> intf )
    {
        final Map<String,String> result = new HashMap<String,String>();
        
        final Method[] methods = intf.getMethods();
        for( final Method m : methods )
        {
            // cdebug( "Method: " + m );
            if ( JMXUtil.isIsOrGetter(m) )
            {
                final String attrName = JMXUtil.getAttributeName(m);
                
                final org.jvnet.hk2.config.Attribute attrAnn = m.getAnnotation( org.jvnet.hk2.config.Attribute.class );
                if ( attrAnn != null )
                {
                    // does it make sense to supply default values for required attributes?
                    final String value = attrAnn.defaultValue();
                    
                    // don't put null values into defaults (see @Attribute annotation)
                    final boolean emptyDefault = value.equals( "\u0000" );
                    cdebug( "Method " + m + " has default value of " + (emptyDefault ? "\\u0000" : value) );
                    if ( ! emptyDefault )
                    {
                        result.put( attrName, "" + attrAnn.defaultValue() );
                    }
                }
                /*
                else
                {
                    result.put( attrName, "N/A" );
                }
                */
            }
        }
        
        return result;
    }
    
        public final Map<String,String>
    getDefaultValues( final String j2eeTypeIn )
    {
        final String j2eeType = (j2eeTypeIn == null) ? getJ2EEType() : j2eeTypeIn;
        
        final ContainedTypeInfo   info = new ContainedTypeInfo( getConfigBean() );
        final Class<? extends ConfigBeanProxy>  intf = info.getConfigBeanProxyClassFor( j2eeTypeIn );
        if ( intf == null )
        {
            throw new IllegalArgumentException( "Illegal j2eeType: " + j2eeType );
        }
        
        return _getDefaultValues( intf ); 
    }
    
    private volatile boolean _namesInited = false;
    
    /**
        Make sure the AMX to XML and vice-versa mapping is in place
     */
        private synchronized void
    initNames()
    {
        if ( ! _namesInited ) synchronized(this)
        {
            final DelegateToConfigBeanDelegate delegate = getConfigDelegate();
            final String[] attrNames = getAttributeNames();
            
            for( final String attrName : attrNames )
            {
                // side effect: causes name mapping
                delegate.supportsAttribute( attrName );
            }
            
            _namesInited = true;
        }
    }
    
    @Override
    protected synchronized void
	postRegisterHook( Boolean registrationSucceeded )
	{
		super.postRegisterHook( registrationSucceeded );
		
        if ( registrationSucceeded.booleanValue() )
        {
            initNames();
        }
	}
    
    /**
        Issue an AttributeChangeNotification.  The name is the xml name; this method should
        be called only from "bubble up" code from the lower level ConfigBean stuff.
        @see AMXConfigLoader
     */
        void
    issueAttributeChangeForXmlAttrName(
        final String     xmlAttrName,
        final Object     oldValue,
        final Object     newValue,
        final long       whenChanged )
    {
        if ( ! _namesInited ) initNames();
        
        final DelegateToConfigBeanDelegate delegate = getConfigDelegate();
        
        String attrType = String.class.getName();
        String amxAttrName = NameMapping.getInstance(getJ2EEType()).getAMXName( xmlAttrName );
        if ( amxAttrName == null )
        {
            cdebug( "issueAttributeChangeForXmlAttrName: can't find AMX name for: " + xmlAttrName + ", using xmlName for now" );
            amxAttrName = xmlAttrName;
        }
        else
        {
            attrType = getAttributeType(amxAttrName);
        }
        
        if ( oldValue != newValue )
        {
			sendAttributeChangeNotification( "", amxAttrName, attrType, whenChanged, oldValue, newValue );
        }
    }
    
    
    /**
        The dotted name for config should be the xml name.
     */
    @Override
        protected String
    attributeNameToDottedValueName( final String amxAttrName )
    {
        final String xmlName = NameMapping.getInstance(getJ2EEType()).getXMLName( amxAttrName );
        return xmlName == null ? super.attributeNameToDottedValueName(amxAttrName) : xmlName;
    }



}





















