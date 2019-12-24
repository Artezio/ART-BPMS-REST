package com.artezio.forms.formio;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FormComponent {
    
    private final Set<String> CONTAINER_TYPES = new HashSet<String>(){{
        add("container");
        add("form");
        add("survey");
    }};
    
    private final Set<String> ARRAY_TYPES = new HashSet<String>(){{
        add("datagrid");
        add("editgrid");
    }};
    
    private final Set<String> SIMPLE_LAYOUT_TYPES = new HashSet<String>(){{
        add("fieldset");
        add("panel");
        add("well");
    }};
    
    private final Set<String> LAYOUT_TYPES = new HashSet<String>(){{
        addAll(SIMPLE_LAYOUT_TYPES);
        add("columns");
        add("table");
    }};
    
    @JsonProperty("_id")
    String id;
    String key;
    Boolean disabled;
    String type;
    Boolean input;
    String src;
    String action;
    String state;
    
    List<FormComponent> components;
    List<List<FormComponent>> columns;
    List<List<List<FormComponent>>> rows;
    
    Map<String, Object> properties;
    
    public List<FormComponent> getChildComponents() {
        List<FormComponent> result = getNonLayoutChildComponents();
        result.addAll(getLayoutChildComponents());
        return result;
    }

    private List<FormComponent> getNonLayoutChildComponents() {
        return getComponents().stream()
                .filter(component -> !LAYOUT_TYPES.contains(component.getType()))
                .collect(Collectors.toList());
    }

    private List<FormComponent> getLayoutChildComponents() {
        List<FormComponent> result = getSimpleLayoutChildComponents();
        result.addAll(getColumnsChildComponents());
        result.addAll(getTableChildComponents());
        return result;
    }

    private List<FormComponent> getSimpleLayoutChildComponents() {
        return getComponents().stream()
                .filter(component -> SIMPLE_LAYOUT_TYPES.contains(component.getType()))
                .map(component -> component.getComponents())
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<FormComponent> getTableChildComponents() {
        return getComponents().stream()
                .filter(component -> "table".equals(component.getType()))
                .map(component -> component.getRows())
                .flatMap(Collection::stream)
                .flatMap(Collection::stream)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<FormComponent> getColumnsChildComponents() {
        return getComponents().stream()
                .filter(component -> "columns".equals(component.getType()))
                .map(component -> component.getColumns())
                .flatMap(Collection::stream)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
    
    public boolean isContainer() {
        return StringUtils.equalsAny(type, CONTAINER_TYPES.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
    }
    
    public boolean isArray() {
        return StringUtils.equalsAny(type, ARRAY_TYPES.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
    }
    
    public boolean isEditable() {
        return BooleanUtils.isNotTrue(disabled);
    }
    
    public boolean isInput() {
        return BooleanUtils.isTrue(input);
    }
    
    public boolean hasKey() {
        return StringUtils.isNotBlank(key);
    }
    
    public boolean hasComponents() {
        return CollectionUtils.isNotEmpty(components);
    }
    
    public boolean mustBeWrapped() {
        return getChildComponents().stream()
                .anyMatch(component -> "flat".equals(component.getKey()));
    }
    
    public boolean isSubform() {
        return "form".equals(type) && src != null;
    }
    //FIXME check all components
    public boolean containsFileComponent(String componentKey) {
        return getChildComponents().stream()
            .filter(component -> componentKey.equals(component.getKey()))
            .anyMatch(component -> "file".equals(component.getType()));
    }
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    
    public Boolean getInput() {
        return input;
    }

    public void setInput(Boolean input) {
        this.input = input;
    }

    public List<FormComponent> getComponents() {
        return components;
    }

    public void setComponents(List<FormComponent> components) {
        this.components = components;
    }
    
    public List<List<FormComponent>> getColumns() {
        return columns;
    }

    public void setColumns(List<List<FormComponent>> columns) {
        this.columns = columns;
    }

    public List<List<List<FormComponent>>> getRows() {
        return rows;
    }

    public void setRows(List<List<List<FormComponent>>> rows) {
        this.rows = rows;
    }
    
    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }
    
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
    
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "FormComponent [id=" + id + ", key=" + key + ", disabled=" + disabled + ", type=" + type + ", input=" + input + ", src=" + src
                + ", action=" + action + ", state=" + state + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((action == null) ? 0 : action.hashCode());
        result = prime * result + ((disabled == null) ? 0 : disabled.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((input == null) ? 0 : input.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((properties == null) ? 0 : properties.hashCode());
        result = prime * result + ((src == null) ? 0 : src.hashCode());
        result = prime * result + ((state == null) ? 0 : state.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FormComponent other = (FormComponent) obj;
        if (action == null) {
            if (other.action != null)
                return false;
        } else if (!action.equals(other.action))
            return false;
        if (disabled == null) {
            if (other.disabled != null)
                return false;
        } else if (!disabled.equals(other.disabled))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (input == null) {
            if (other.input != null)
                return false;
        } else if (!input.equals(other.input))
            return false;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (properties == null) {
            if (other.properties != null)
                return false;
        } else if (!properties.equals(other.properties))
            return false;
        if (src == null) {
            if (other.src != null)
                return false;
        } else if (!src.equals(other.src))
            return false;
        if (state == null) {
            if (other.state != null)
                return false;
        } else if (!state.equals(other.state))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        return true;
    }


}
