package io.oci.service;

import io.oci.model.User;
import java.util.List;

public interface UserStorage {
    User findByUsername(String username);

    User findById(Object id);

    List<User> listAll();

    void persist(User user);

    void deleteByUsername(String username);
}

