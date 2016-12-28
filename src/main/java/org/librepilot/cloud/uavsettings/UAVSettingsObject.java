package org.librepilot.cloud.uavsettings;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by marc on 22.08.2016.
 */
public class UAVSettingsObject {
    public String name;
    public String id;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }


    public Collection<UAVSettingsField> getField() {
        return fields.values();
    }

    @JsonIgnore
    public Map<String, UAVSettingsField> getFields() {
        return fields;
    }

    public Map<String, UAVSettingsField> fields;

    public UAVSettingsObject() {
        this.fields = new TreeMap<>();
    }
}
