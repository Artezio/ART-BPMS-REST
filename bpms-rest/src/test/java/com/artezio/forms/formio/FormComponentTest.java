package com.artezio.forms.formio;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.junit.Assert.*;
import static org.powermock.api.easymock.PowerMock.*;
import static org.easymock.EasyMock.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;


@RunWith(PowerMockRunner.class)
@PrepareForTest(FormComponent.class)
public class FormComponentTest {
    
    private FormComponent formComponent = new FormComponent();

    @Test
    public void testGetChildComponents() throws Exception {
        formComponent = createPartialMock(FormComponent.class, "getNonLayoutChildComponents", "getLayoutChildComponents");
        
        FormComponent component1 = new FormComponent();
        component1.setId("id1");
        FormComponent component2 = new FormComponent();
        component2.setId("id2");
        
        expectPrivate(formComponent, "getNonLayoutChildComponents").andReturn(new ArrayList() {{add(component1);}});
        expectPrivate(formComponent, "getLayoutChildComponents").andReturn(new ArrayList() {{add(component2);}});
        
        PowerMock.replay(formComponent);
        
        List<FormComponent> actual = formComponent.getChildComponents();
        
        assertEquals(Arrays.asList(component1, component2), actual);
        
        PowerMock.verify(formComponent);
    }
    
    @Test
    public void testGetNonLayoutChildComponents() throws Exception {
        FormComponent layoutСomponent = new FormComponent();
        layoutСomponent.setId("layoutId");
        layoutСomponent.setType("fieldset");
        
        FormComponent nonLayoutСomponentСomponent = new FormComponent();
        nonLayoutСomponentСomponent.setId("nonLayoutId");
        nonLayoutСomponentСomponent.setType("container");
        
        formComponent.setComponents(Arrays.asList(layoutСomponent, nonLayoutСomponentСomponent));
        
        List<FormComponent> actual = Whitebox.<List<FormComponent>>invokeMethod(formComponent, "getNonLayoutChildComponents");
        
        assertEquals(Arrays.asList(nonLayoutСomponentСomponent), actual);
    }
    
    @Test
    public void testGetLayoutChildComponents() throws Exception {
        formComponent = createPartialMock(FormComponent.class, "getSimpleLayoutChildComponents", "getColumnsChildComponents", "getTableChildComponents");
        
        FormComponent component1 = new FormComponent();
        component1.setId("id1");
        FormComponent component2 = new FormComponent();
        component2.setId("id2");
        FormComponent component3 = new FormComponent();
        component3.setId("id3");
        
        expectPrivate(formComponent, "getSimpleLayoutChildComponents").andReturn(new ArrayList() {{add(component1);}});
        expectPrivate(formComponent, "getColumnsChildComponents").andReturn(new ArrayList() {{add(component2);}});
        expectPrivate(formComponent, "getTableChildComponents").andReturn(new ArrayList() {{add(component3);}});
        
        PowerMock.replay(formComponent);
        
        List<FormComponent> actual = Whitebox.<List<FormComponent>>invokeMethod(formComponent, "getLayoutChildComponents");
        
        assertEquals(Arrays.asList(component1, component2, component3), actual);
        
        PowerMock.verify(formComponent);
    }
    
    
    @Test
    public void testGetSimpleLayoutChildComponents() throws Exception {
        FormComponent sipmleLayoutСomponent = new FormComponent();
        sipmleLayoutСomponent.setId("sipmleLayoutId");
        sipmleLayoutСomponent.setType("fieldset");
        FormComponent component1 = new FormComponent();
        component1.setId("id1");
        sipmleLayoutСomponent.setComponents(Arrays.asList(component1));
        
        FormComponent notSipmleLayoutСomponent = new FormComponent();
        notSipmleLayoutСomponent.setId("notSipmleLayoutId");
        notSipmleLayoutСomponent.setType("container");
        FormComponent component2 = new FormComponent();
        component2.setId("id2");
        notSipmleLayoutСomponent.setComponents(Arrays.asList(component2));
        
        formComponent.setComponents(Arrays.asList(sipmleLayoutСomponent, notSipmleLayoutСomponent));
        
        List<FormComponent> actual = Whitebox.<List<FormComponent>>invokeMethod(formComponent, "getSimpleLayoutChildComponents");
        
        assertEquals(Arrays.asList(component1), actual);
    }
    
    @Test
    public void testGetColumnsChildComponents() throws Exception {
        FormComponent sipmleLayoutСomponent = new FormComponent();
        sipmleLayoutСomponent.setId("sipmleLayoutId");
        sipmleLayoutСomponent.setType("fieldset");
        FormComponent component1 = new FormComponent();
        component1.setId("id1");
        sipmleLayoutСomponent.setComponents(Arrays.asList(component1));
        
        FormComponent columnsСomponent = new FormComponent();
        columnsСomponent.setId("nonLayoutId");
        columnsСomponent.setType("columns");
        FormComponent component2 = new FormComponent();
        component2.setId("id2");
        columnsСomponent.setColumns(Arrays.asList(Arrays.asList(component2)));
        
        formComponent.setComponents(Arrays.asList(sipmleLayoutСomponent, columnsСomponent));
        
        List<FormComponent> actual = Whitebox.<List<FormComponent>>invokeMethod(formComponent, "getColumnsChildComponents");
        
        assertEquals(Arrays.asList(component2), actual);
    }
    
    @Test
    public void testGetTableChildComponents() throws Exception {
        FormComponent sipmleLayoutСomponent = new FormComponent();
        sipmleLayoutСomponent.setId("sipmleLayoutId");
        sipmleLayoutСomponent.setType("fieldset");
        FormComponent component1 = new FormComponent();
        component1.setId("id1");
        sipmleLayoutСomponent.setComponents(Arrays.asList(component1));
        
        FormComponent tableСomponent = new FormComponent();
        tableСomponent.setId("tableId");
        tableСomponent.setType("table");
        FormComponent component2 = new FormComponent();
        component2.setId("id2");
        tableСomponent.setRows(Arrays.asList(Arrays.asList(Arrays.asList(component2))));
        
        formComponent.setComponents(Arrays.asList(sipmleLayoutСomponent, tableСomponent));
        
        List<FormComponent> actual = Whitebox.<List<FormComponent>>invokeMethod(formComponent, "getTableChildComponents");
        
        assertEquals(Arrays.asList(component2), actual);
    }
    
    @Test
    public void testIsContainer() {
        formComponent.setType("container");
        assertTrue(formComponent.isContainer());
    }
    
    @Test
    public void testIsContainer_notContainerType() {
        formComponent.setType("other_type");
        assertFalse(formComponent.isContainer());
    }
    
    @Test
    public void testIsArray() {
        formComponent.setType("datagrid");
        assertTrue(formComponent.isArray());
    }
    
    @Test
    public void testIsArray_notArrayType() {
        formComponent.setType("other_type");
        assertFalse(formComponent.isArray());
    }
    
    @Test
    public void testIsEditable() {
        formComponent.setDisabled(false);
        assertTrue(formComponent.isEditable());
    }
    
    @Test
    public void testIsEditable_disabledIsNull() {
        formComponent.setDisabled(null);
        assertTrue(formComponent.isEditable());
    }
    
    @Test
    public void testIsEditable_disabledIsTrue() {
        formComponent.setDisabled(true);
        assertFalse(formComponent.isEditable());
    }
    
    @Test
    public void testIsInput() {
        formComponent.setInput(true);
        assertTrue(formComponent.isInput());
    }
    
    @Test
    public void testIsInput_inputIsNull() {
        formComponent.setInput(null);
        assertFalse(formComponent.isInput());
    }
    
    @Test
    public void testIsInput_inputIsFalse() {
        formComponent.setInput(false);
        assertFalse(formComponent.isInput());
    }
    
    @Test
    public void testHasKey() {
        formComponent.setKey("key");
        assertTrue(formComponent.hasKey());
    }
    
    @Test
    public void testHasKey_keyIsBlank() {
        formComponent.setKey("");
        assertFalse(formComponent.hasKey());
    }
    
    @Test
    public void testHasKey_keyIsNull() {
        formComponent.setKey(null);
        assertFalse(formComponent.hasKey());
    }
    
    @Test
    public void testHasComponents() {
        FormComponent component1 = new FormComponent();
        component1.setId("id1");
        formComponent.setComponents(Arrays.asList(component1));
        
        assertTrue(formComponent.hasComponents());
    }
    
    @Test
    public void testHasComponents_componentListIsEmpty() {
        formComponent.setComponents(Collections.emptyList());
        assertFalse(formComponent.hasComponents());
    }
    
    @Test
    public void testHasComponents_componentListIsNull() {
        formComponent.setComponents(null);
        assertFalse(formComponent.hasComponents());
    }

    @Test
    public void testIsSubform() {
        formComponent.setType("form");
        formComponent.setSrc("");
        
        assertTrue(formComponent.isSubform());
    }
    
    @Test
    public void testIsSubform_notFormType() {
        formComponent.setType("any_type");
        formComponent.setSrc("");
        
        assertFalse(formComponent.isSubform());
    }
    
    @Test
    public void testIsSubform_noSrc() {
        formComponent.setType("form");
        formComponent.setSrc(null);
        
        assertFalse(formComponent.isSubform());
    }
    
    @Test
    public void testContainsFileComponent() {
        String fileComponentKey = "file_component_key";
        FormComponent fileComponent = new FormComponent();
        fileComponent.setType("file");
        fileComponent.setKey(fileComponentKey);
        formComponent.setComponents(Arrays.asList(fileComponent));
        
        assertTrue(formComponent.containsFileComponent(fileComponentKey));
    }

    @Test
    public void testContainsFileComponent_FileComponentIsPlacedDeep() {
        String fileComponentKey = "file_component_key";
        String container1ComponentKey = "container1";
        String container2ComponentKey = "container2";
        FormComponent container1Component = new FormComponent();
        container1Component.setKey(container1ComponentKey);
        container1Component.setType("container");
        FormComponent container2Component = new FormComponent();
        container2Component.setKey(container2ComponentKey);
        container2Component.setType("container");
        FormComponent fileComponent = new FormComponent();
        fileComponent.setType("file");
        fileComponent.setKey(fileComponentKey);
        container1Component.setComponents(asList(container2Component));
        container2Component.setComponents(asList(fileComponent));
        formComponent.setComponents(Arrays.asList(container1Component));

        assertTrue(formComponent.containsFileComponent(fileComponentKey));
    }
    
    @Test
    public void testContainsFileComponent_notFileType() {
        String fileComponentKey = "file_component_key";
        FormComponent fileComponent = new FormComponent();
        fileComponent.setType("any_type");
        fileComponent.setKey(fileComponentKey);
        formComponent.setComponents(Arrays.asList(fileComponent));
        
        assertFalse(formComponent.containsFileComponent(fileComponentKey));
    }

    @Test
    public void testContainsFileComponent_noComponent() {
        String fileComponentKey = "file_component_key";
        FormComponent fileComponent = new FormComponent();
        fileComponent.setType("file");
        fileComponent.setKey(fileComponentKey);
        formComponent.setComponents(Arrays.asList(fileComponent));
        
        assertFalse(formComponent.containsFileComponent("other_file_component_key"));
    }

}
