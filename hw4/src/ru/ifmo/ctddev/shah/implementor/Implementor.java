package ru.ifmo.ctddev.shah.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Implementor implements JarImpler {

    private Map<String, MethodOnSteroids> methods = new HashMap<>();

    @Override
    public void implementJar(Class<?> token, File jarFile) throws ImplerException {
        try {
            File root = Files.createTempDirectory("ImplDir").toFile();
            implement(token, root);
            String relativePath = "";
            if (token.getPackage() != null) {
                relativePath = token.getPackage().getName().replaceAll("\\.", File.separator)
                 + File.separator + token.getSimpleName() + "Impl";
            }
            File implFile = new File(root.getPath() + File.separator + relativePath  + ".java");
            compileFile(root, implFile);
            createJar(jarFile, root, relativePath, implFile);
        } catch (IOException e) {
            throw new ImplerException(e.getMessage(), e.getCause());
        } finally {
            methods.clear();
        }
    }

    /**
     * Create new jar file in specific directory.
     * Create and add in jar file implFile.class.
     * @param jarFile name of jar file
     * @param root working directory
     * @param relativePath entry for jar
     * @param implFile implemented file
     * @throws ImplerException when jar file cannot be created or cannot compile
     * implementation
     */
    private void createJar(File jarFile, File root, String relativePath, File implFile) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jout = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            String compiledClassName = root.getPath() + File.separator + relativePath + ".class";
            JarEntry entry = new JarEntry(relativePath + ".class");
            entry.setTime(implFile.lastModified());
            jout.putNextEntry(entry);
            InputStream in = new BufferedInputStream(new FileInputStream(compiledClassName));
            byte[] buffer = new byte[1024];
            int count = 0;
            while ((count = in.read(buffer)) > 0) {
                jout.write(buffer, 0, count);
            }
            in.close();
            jout.closeEntry();
        } catch (IOException e) {
            throw new ImplerException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Compile java file in directory.
     * @param root working directory
     * @param implFile file to compile
     * @throws ImplerException when cannot compile file
     */
    private void compileFile(File root, File implFile) throws ImplerException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final List<String> args = new ArrayList<>();
        args.add(implFile.getAbsolutePath());
        args.add("-cp");
        args.add(root.getPath() + File.pathSeparator + System.getProperty("java.class.path"));
        int success = compiler.run(null, null, null, args.toArray(new String[args.size()]));
        if (success != 0) {
            throw new ImplerException("Can't compile file: " + implFile.getPath());
        }
    }

    @Override
    public void implement(final Class<?> token, final File root) throws ImplerException {
        try {
            StringBuilder classImplementation = getClassImplementation(token);
            File newRoot = createDirs(token.getPackage(), root);
            File implFile = new File(newRoot.getPath() + File.separator + token.getSimpleName() + "Impl" + ".java");
            try (BufferedWriter fileOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(implFile), "UTF-8"))) {
                fileOut.write(classImplementation.toString());
            } catch (IOException e) {
                throw new ImplerException("Error during writing file", e.getCause());
            }
        } finally {
            methods.clear();
        }
    }

    /**
     * Verify parametrs of launch
     * @param args launch parametrs
     * @return 0 if parametrs are correct, -1 otherwise
     */
    private static int checkUsage(final String[] args) {
        if (args == null || args.length < 2 || args.length > 3) {
            return -1;
        }
        for (String arg : args) {
            if (arg == null) {
                return -1;
            }
        }
        return 0;
    }

    public static void main(final String[] args) {
        if (checkUsage(args) == 0) {
            try {
                if (args[0].equals("-jar")) {
                    getInstance().implementJar(Class.forName(args[1]), new File(args[2]));
                } else {
                    getInstance().implement(Class.forName(args[0]), new File(args[1]));
                }
            } catch (ImplerException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Invalid usage");
        }
    }

    /**
     * Returns an Implementor object.
     * @return a new Implementor instance
     */
    private static Implementor getInstance() {
        return new Implementor();
    }

    /**
     * Create directories for package.
     * @param pack class package
     * @param root working directory
     * @return directory for this package
     * @throws ImplerException when cannot create directories
     */
    private File createDirs(final Package pack, final File root) throws ImplerException {
        if (pack == null) {
            return root;
        }
        String packagePath = pack.getName().replaceAll("\\.", File.separator);
        File dir = new File(root.getPath() + File.separator + packagePath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new ImplerException("Cannot create dirs");
            }
        }
        return dir;
    }

    /**
     * Generate implementation for class.
     * @param loadedClass class for implementation
     * @return implementation of class
     * @throws ImplerException when implementation cannot be generated
     */
    private StringBuilder getClassImplementation(final Class<?> loadedClass) throws ImplerException {
        if (loadedClass.isPrimitive() || Modifier.isFinal(loadedClass.getModifiers())) {
            throw new ImplerException("Cannot implement "
                    + (loadedClass.isPrimitive() ? "primitive" : "final") + " class");
        }
        String className = loadedClass.getSimpleName();
        String implClassName = className + "Impl" ;

        StringBuilder writer = new StringBuilder();
        if (loadedClass.getPackage() != null) {
            writer.append("package ").append(loadedClass.getPackage().getName()).append(";").append("\n");
        }
        writer.append("import ").append(loadedClass.getName()).append(";");
        writer.append("\n");
        writer.append("public class ")
                .append(implClassName)
                .append(loadedClass.isInterface() ? " implements " : " extends ")
                .append(className)
                .append(" {");
        writer.append("\n");

        implementConstructors(loadedClass, implClassName, writer);

        implementMethods(loadedClass, writer);

        writer.append("}");
        writer.append("\n");
        return writer;

    }

    /**
     * Generate implementation for all class methods.
     * @param loadedClass implemented class
     * @param writer where implementation is written
     */
    private void implementMethods(final Class<?> loadedClass, final StringBuilder writer) {
        loadAllMethods(loadedClass, true);
        loadAllMethods(loadedClass, false);
        for (Map.Entry<String, MethodOnSteroids> item : methods.entrySet()) {
                writer.append(getMethodImplementation(item.getValue()));
        }
    }

    /**
     * Generate implementation of constructors.
     * Generate all possible constructors matching super.
     * @param clazz implemented class
     * @param implClazzName generated class name
     * @param writer where implemetation is written
     * @throws ImplerException if there are no public or protected constructors
     * in super class
     */
    private void implementConstructors(final Class<?> clazz, final String implClazzName, final StringBuilder writer) throws ImplerException {
        if (clazz.isInterface()) {
            return;
        }
        int counterConstructors = 0;
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            int modifiers = constructor.getModifiers();
            if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
                counterConstructors++;
                writer.append(getAnnotationsImplementation(constructor.getDeclaredAnnotations(), false));
                writer.append(implClazzName);
                writer.append(getParametrsImplementation(constructor.getParameters(), false));
                writer.append(getExceptionsImplementation(constructor.getExceptionTypes()));
                writer.append(" {");
                writer.append("\n");
                writer.append("super");
                writer.append(getParametrsImplementation(constructor.getParameters(), true));
                writer.append(";");
                writer.append("\n");
                writer.append("}");
                writer.append("\n");
            }
        }
        if (!clazz.isInterface() && counterConstructors == 0) {
            throw new ImplerException("Class has only private constructors");
        }
    }

    /**
     * Implement all annnotations.
     * @param annotations array of annotations
     * @param isOverrided flag of add override
     * @return annotation implementation
     */
    private String getAnnotationsImplementation(final Annotation[] annotations, final boolean isOverrided) {
        StringBuilder writer = new StringBuilder();
        if (isOverrided) {
            writer.append("@Override\n");
        }
        for (Annotation annotation : annotations) {
            writer.append(annotation).append("\n");
        }
        return writer.toString();
    }

    /**
     * Searches all methods suitable for options.
     * Method browse all method in class and
     * super classes and interfaces and
     * add/remove methods in map.
     * @param clazz class for search
     * @param addAbstract add abstract methods or remove
     */
    private void loadAllMethods(final Class<?> clazz, final boolean addAbstract) {
        if (clazz == null) {
            return;
        }
        loadAllMethods(clazz.getSuperclass(), addAbstract);
        for (Class<?> item : clazz.getInterfaces()) {
            loadAllMethods(item, addAbstract);
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (addAbstract) {
                addMethod(method);
            } else {
                removeMethod(method);
            }
        }
    }

    /**
     * Remove method from map.
     * If method with same signature was added before
     * then removing it from map.
     * @param method Method
     */
    private void removeMethod(Method method) {
        int modifiers = method.getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isPrivate(modifiers)) {
            return;
        }
        final String methodSignature = getMethodSignature(method);
        if (methods.containsKey(methodSignature)) {
            MethodOnSteroids steroids = methods.get(methodSignature);
            if (steroids.getMethod().getDeclaringClass()
                    .isAssignableFrom(method.getDeclaringClass())) {
                methods.remove(methodSignature);
            }
        }
    }

    /**
     * Add method if it is abstract.
     * If method was added than exceptions and
     * visible modifiers are merged with new method.
     * @param method method
     */
    private void addMethod(Method method) {
        int modifiers = method.getModifiers();
        if (!Modifier.isAbstract(modifiers) || Modifier.isPrivate(modifiers)) {
            return;
        }
        final String methodSignature = getMethodSignature(method);
        MethodOnSteroids addedMethod = new MethodOnSteroids(method);
        if (methods.containsKey(methodSignature)) {
            MethodOnSteroids steroids = methods.get(methodSignature);

            if (!steroids.getMethod().getDeclaringClass().isAssignableFrom(method.getDeclaringClass())
                    && !method.getDeclaringClass().isAssignableFrom(steroids.getMethod().getDeclaringClass())) {
                addedMethod.setMethodExceptions(null);

                if (Modifier.isProtected(modifiers) && Modifier.isPublic(steroids.getMethodModifiers())) {
                    addedMethod.setMethodModifiers(modifiers);
                }
            }
        }
        methods.put(getMethodSignature(method), addedMethod);
    }

    /**
     * Return method signature without class name.
     * @param method method
     * @return method signature
     */
    private String getMethodSignature(final Method method) {
        return method.getName() + getParametrsImplementation(method.getParameters(), false);
    }

    /**
     * Returns string representing exceptions.
     * @param exceptions exceptions
     * @return string representing exceptions
     */
    private String getExceptionsImplementation(final Class<?>[] exceptions) {
        StringBuilder writer = new StringBuilder();
        int countExceptions = exceptions.length;
        if (countExceptions > 0) {
            writer.append(" throws ");
            for (int i = 0; i < countExceptions; i++) {
                if (i > 0) {
                    writer.append(", ");
                }
                writer.append(exceptions[i].getName());
            }
        }
        return writer.toString();
    }

    /**
     * Get implementation of method with specific modifiers.
     * @param steroids method with modifiers
     * @return a method implementation
     */
    private String getMethodImplementation(final MethodOnSteroids steroids) {
        final Method method = steroids.getMethod();
        int modifiers = steroids.getMethodModifiers();
        StringBuilder writer = new StringBuilder();
        if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
            writer.append(getAnnotationsImplementation(method.getDeclaredAnnotations(), true));
            if (Modifier.isProtected(modifiers)) {
                writer.append("protected ");
            } else {
                writer.append("public ");
            }
            if (method.getGenericReturnType() instanceof TypeVariable) {
                writer.append("<").append(((TypeVariable) method.getGenericReturnType()).getName()).append(">");
            }
            writer.append(method.getGenericReturnType().getTypeName()).append(" ");
            writer.append(method.getName());
            writer.append(getParametrsImplementation(method.getParameters(), false));
            writer.append(getExceptionsImplementation(steroids.getMethodExceptions()));
            writer.append(" {\n");
            writer.append(getReturnValueImplementation(method.getReturnType()));
            writer.append("}\n\n");
        }
        return writer.toString();
    }

    /**
     * Return string representing array of parametrs.
     * If boolean parametr is true then generate string without type of paramets;
     * and within type otherwise.
     * @param parameters parametrs for implement
     * @param onlyNames generate representing with types or names
     * @return string representing parametrs
     */
    private String getParametrsImplementation(final Parameter[] parameters, final boolean onlyNames) {
        StringBuilder writer = new StringBuilder();
        writer.append("(");
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                writer.append(", ");
            }
            if (onlyNames) {
                writer.append(parameters[i].getName());
            } else {
                writer.append(parameters[i].toString());
            }
        }
        writer.append(") ");
        return writer.toString();
    }

    /**
     * Generate implementation for return value.
     * @param returnType type of return value
     * @return default return for this type
     */
    private String getReturnValueImplementation(final Class<?> returnType) {
        StringBuilder writer = new StringBuilder();

        if (returnType != Void.TYPE) {
            writer.append("return ");
            if (returnType.isPrimitive()) {
                writer.append(getDefaultValue(returnType));
            } else {
                writer.append("null");
            }
            writer.append(";\n");
        }
        return writer.toString();
    }

    /**
     * Returns default value of primitive type.
     * @param clazz return type
     * @return default value for this type
     * @throws java.lang.IllegalArgumentException if clss isn't primitive
     */
    private static String getDefaultValue(final Class clazz) {
        if (clazz.equals(boolean.class)) {
            return "false";
        } else if (clazz.equals(byte.class)) {
            return "(byte)0";
        } else if (clazz.equals(short.class)) {
            return "(short)0";
        } else if (clazz.equals(int.class)) {
            return "0";
        } else if (clazz.equals(long.class)) {
            return "0L";
        } else if (clazz.equals(float.class)) {
            return "0.0f";
        } else if (clazz.equals(double.class)) {
            return "0.0d";
        } else if (clazz.equals(char.class)) {
            return "'\\u0000'";
        } else {
            throw new IllegalArgumentException(
                    "Class type " + clazz + " not supported");
        }
    }

    private class MethodOnSteroids {
        private Method method;
        private int methodModifiers;
        private Class<?>[] methodExceptions;

        public MethodOnSteroids(Method method) {
            this.method = method;
            this.methodModifiers = method.getModifiers();
            this.methodExceptions = method.getExceptionTypes();
        }

        public Method getMethod() {
            return method;
        }

        public int getMethodModifiers() {
            return methodModifiers;
        }

        public Class<?>[] getMethodExceptions() {
            return methodExceptions;
        }

        public void setMethodModifiers(int methodModifiers) {
            this.methodModifiers = methodModifiers;
        }

        public void setMethodExceptions(Class<?>[] methodExceptions) {
            if (methodExceptions == null) {
                this.methodExceptions = new Class<?>[0];
            } else {
                this.methodExceptions = methodExceptions;
            }
        }
    }
}
