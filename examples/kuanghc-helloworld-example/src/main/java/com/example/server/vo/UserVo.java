package com.example.server.http.vo;

public class UserVo {
    private String userName;
    private String id;

    public UserVo() {
    }

    public UserVo(String userName, String id) {
        this.userName = userName;
        this.id = id;
    }

    public String getUserName() {
        return this.userName;
    }

    public String getId() {
        return this.id;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "UserVo{" +
                "userName='" + userName + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
