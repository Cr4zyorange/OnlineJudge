package com.onlinejudge.auth.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Applies the OpenAPI additionalProperties:false rule to this v2 DTO only. */
public class StrictServiceTokenRequestDeserializer extends StdDeserializer<ServiceTokenRequest> {
    private static final Set<String> FIELDS = Set.of("audience", "scopes");

    public StrictServiceTokenRequestDeserializer() {
        super(ServiceTokenRequest.class);
    }

    @Override
    public ServiceTokenRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode value = parser.getCodec().readTree(parser);
        if (value == null || !value.isObject()) {
            throw JsonMappingException.from(parser, "ServiceTokenRequest must be an object");
        }
        Iterator<String> names = value.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!FIELDS.contains(field)) {
                throw JsonMappingException.from(parser, "ServiceTokenRequest does not allow field: " + field);
            }
        }
        JsonNode audience = value.get("audience");
        JsonNode scopes = value.get("scopes");
        if (audience == null || !audience.isTextual() || scopes == null || !scopes.isArray()) {
            throw JsonMappingException.from(parser, "ServiceTokenRequest requires string audience and array scopes");
        }
        List<String> parsedScopes = new ArrayList<>();
        for (JsonNode scope : scopes) {
            if (!scope.isTextual()) {
                throw JsonMappingException.from(parser, "ServiceTokenRequest scopes must contain strings");
            }
            parsedScopes.add(scope.textValue());
        }
        return new ServiceTokenRequest(audience.textValue(), List.copyOf(parsedScopes));
    }
}
