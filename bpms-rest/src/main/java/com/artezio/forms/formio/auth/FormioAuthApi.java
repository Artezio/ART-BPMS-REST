package com.artezio.forms.formio.auth;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
interface FormioAuthApi {
    @POST
    @Path("/user/login")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response login(Credentials credentials);

    @GET
    @Path("/logout")
    Response logout(@HeaderParam("x-jwt-token") @NotNull String jwtToken);
}