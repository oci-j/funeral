package io.oci.service;

import java.util.List;

import io.oci.model.User;

public interface UserStorage {
    User findByUsername(
            String username
    );

    User findById(
            Object id
    );

    List<User> listAll();

    void persist(
            User user
    );

    void deleteByUsername(
            String username
    );
}
