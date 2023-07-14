package com.example.server.resource;

import com.example.server.data.User;
import com.example.server.http.vo.UserVo;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("user")
public class UserResource {

    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json")
    public Response register(User user) {
        UserVo userVo = new UserVo();
        userVo.setUserName(user.getUserName());
        userVo.setId(UUID.randomUUID().toString());
        return Response.ok(userVo).build();
    }

    @POST
    @Path("login")
    public String login() {
        return "login success";
    }
}
