package io.oci.service;

import java.util.List;

import io.oci.model.User;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MongoUserStorage implements UserStorage {

    @Override
    public User findByUsername(
            String username
    ) {
        return User.find(
                "username",
                username
        ).firstResult();
    }

    @Override
    public User findById(
            Object id
    ) {
        return User.findById(
                id
        );
    }

    @Override
    public List<User> listAll() {
        return User.listAll();
    }

    @Override
    public void persist(
            User user
    ) {
        if (user.id == null) {
            user.id = new org.bson.types.ObjectId();
        }
        user.persistOrUpdate();
    }

    @Override
    public void deleteByUsername(
            String username
    ) {
        User.delete(
                "username",
                username
        );
    }
}
