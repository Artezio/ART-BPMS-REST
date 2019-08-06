package com.artezio.formio.client.auth;

import java.util.HashMap;
import java.util.Map;

public class Credentials {
    private Map<String, String> data = new HashMap<>();

    public Credentials(String login, String password) {
        this.data.put("email", login);
        this.data.put("password", password);
    }

    public Map<String, String> getData() {
        return data;
    }
}
