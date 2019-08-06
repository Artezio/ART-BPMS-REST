package com.artezio.formio.client;

import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Path("/")
public interface FormApi {

    @POST
    @Path("/{formPath}/submission")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode submission(@PathParam("formPath") @NotNull @Valid String formPath,
                        @QueryParam("novalidate") boolean noValidate,
                        Map<String, Object> variables);

    @GET
    @Path("/{formPath}")
    @Produces(MediaType.APPLICATION_JSON)
    JsonNode getForm(
            @PathParam("formPath") @NotNull @Valid String formPath,
            @QueryParam("full") @NotNull Boolean full
    );

    @POST
    @Path("/form")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    JsonNode createForm(String formDefinition);

}
