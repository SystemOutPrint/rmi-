# rmi-反序列化漏洞
大概的思想就是将恶意代码打包成对象，作为RMI的参数，在目标服务器反序列化这个对象的时候触发这段代码，以达到不可告人的小秘密。

# 0x01
AnnotationInvocationHandler这个类继承于Serializable，是可以作为序列化的参数的，该类的readObject方法是长这个样子的：
```java
private void readObject(java.io.ObjectInputStream s)
    throws java.io.IOException, ClassNotFoundException {
    s.defaultReadObject();

    // Check to make sure that types have not evolved incompatibly
    AnnotationType annotationType = null;

    try {
        annotationType = AnnotationType.getInstance(type);
    } catch(IllegalArgumentException e) {
        // Class is no longer an annotation type; time to punch out
        throw new java.io.InvalidObjectException("Non-annotation type in annotation serial stream");
    }

    Map<String, Class<?>> memberTypes = annotationType.memberTypes();
    // If there are annotation members without values, that
    // situation is handled by the invoke method.
    for (Map.Entry<String, Object> memberValue : memberValues.entrySet()) {
        String name = memberValue.getKey();
        Class<?> memberType = memberTypes.get(name);
        if (memberType != null) {  // i.e. member still exists
            Object value = memberValue.getValue();
            if (!(memberType.isInstance(value) ||
                  value instanceof ExceptionProxy)) {
                memberValue.setValue(
                    new AnnotationTypeMismatchExceptionProxy(
                        value.getClass() + "[" + value + "]").setMember(
                            annotationType.members().get(name)));
            }
        }
    }
}
```
关键的点就在于最后的memberValue.setValue，memberValue是map中的value，如果能在这个方法里调用自己的代码，就可以达到目的了。
恰好，common-collections包中的TransformMap类符合这个要求，这个类的setValue会调用一系列装饰器，而这些装饰器恰好可以执行反射代码。所以只需要在构造AnnotationInvocationHandler的时候传入类型为TransformMap的对象就好了。

# 0x02
之前代码中的map是这样构造的：
```java
Map<String, String> inner = new HashMap<String, String>();
inner.put("key", "value");
Map<String, String> outer = TransformMap.decorate(inner);
```    
但是每次执行的时候，都会抛出异常，内容如下：
```java
Registry.rebind disallowed; origin /xxx.xxx.xxx.xxx is non-local host. 
```    
这个异常的意思是RMI注册表的服务不能远程注册，但是这个漏洞是在注册之前触发的，讲道理不应该会抛这个异常，后来查了下，发现map要这样构造：
```java
Map<String, String> inner = new HashMap<String, String>();
inner.put("value", "xxx");
Map<String, String> outer = TransformMap.decorate(inner);
```    
因为AnnotationInvocationHandler.readObject方法中要根据key来取对应注解的方法，注解Target中恰好有个value方法，所以要将key设置为value，Entry的value值随便设置。
