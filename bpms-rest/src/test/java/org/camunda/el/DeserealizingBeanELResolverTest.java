package org.camunda.el;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class DeserealizingBeanELResolverTest {

    @InjectMocks
    private DeserealizingBeanELResolver resolver = new DeserealizingBeanELResolver();
    private TestClassWithDifferentMethods testClass = new TestClassWithDifferentMethods();

    @Test
    public void testDeserializeArgumentsIfRequired() throws NoSuchMethodException {
        Method target = testClass.getClass().getDeclaredMethod("severalArgs", List.class, Integer.class);
        List list = new ArrayList();
        list.add(10L);
        Object[] params = new Object[]{list, 5};
        Object[] actual = resolver.deserializeArgumentsIfRequired(target, params);
        assertSame(actual[0], list);
        assertSame(actual[1], 5);
    }

    @Test
    public void testDeserializeArgumentsIfRequired_deserializeModelFromMap() throws NoSuchMethodException {
        Method target = testClass.getClass().getDeclaredMethod("severalArgs", TestModel.class, String.class);
        Map<String, Object> map = new HashMap<>();
        map.put("stringField", "someString");
        map.put("integerField", 5);
        Object[] params = new Object[]{map, "someString"};
        Object[] actual = resolver.deserializeArgumentsIfRequired(target, params);

        assertEquals(TestModel.class, actual[0].getClass());
        TestModel actualModel = (TestModel) actual[0];
        assertEquals((Integer)5, actualModel.getIntegerField());
        assertEquals("someString", actualModel.getStringField());
        assertSame(actual[1], "someString");
    }

    @Test
    public void testFindMethod() throws NoSuchMethodException {
        Method expected = testClass.getClass().getDeclaredMethod("noArgs");
        Method actual = resolver.findMethod(testClass, "noArgs", new Object[]{});
        assertEquals(expected, actual);
    }

    @Test
    public void testFindMethod_singleArg_argsMatch() throws NoSuchMethodException {
        Method expected = testClass.getClass().getDeclaredMethod("singleArg", List.class);
        Method actual = resolver.findMethod(testClass, "singleArg", new Object[]{new ArrayList<>()});
        assertEquals(expected, actual);
    }

    @Test
    public void testFindMethod_singleArg_mapPassedAsArgument() throws NoSuchMethodException {
        Method expected = testClass.getClass().getDeclaredMethod("singleArg", List.class);
        Method actual = resolver.findMethod(testClass, "singleArg", new Object[]{new HashMap<>()});
        assertEquals(expected, actual);
    }

    @Test
    public void testFindMethod_singleArg_noMatchingArgs() {
        Method actual = resolver.findMethod(testClass, "singleArg", new Object[]{10.3F});
        assertNull(actual);
    }

    @Test
    public void testFindMethod_severalArg_argsMatch() throws NoSuchMethodException {
        Method expected = testClass.getClass().getDeclaredMethod("severalArgs", TestModel.class, String.class);
        Method actual = resolver.findMethod(testClass, "severalArgs", new Object[]{new TestModel(), "someString"});
        assertEquals(expected, actual);
    }

    @Test
    public void testFindMethod_severalArg_argsMatchOverload() throws NoSuchMethodException {
        Method expected = testClass.getClass().getDeclaredMethod("severalArgs", List.class, Integer.class);
        Method actual = resolver.findMethod(testClass, "severalArgs", new Object[]{new ArrayList<>(), 5});
        assertEquals(expected, actual);
    }

    @Test
    public void testFindMethod_severalArg_noMatchingArgs() {
        Method actual = resolver.findMethod(testClass, "severalArgs", new Object[]{30, 5});
        assertNull(actual);
    }

}
