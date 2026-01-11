package io.oci.service;

import io.oci.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class FileUserStorage extends FileStorageBase {

    private static final String COLLECTION = "users";

    @ConfigProperty(name = "oci.storage.no-mongo", defaultValue = "false")
    boolean noMongo;

    public User findByUsername(String username) {
        if (!noMongo) {
            return User.findByUsername(username);
        }

        return findFirst(User.class, COLLECTION, u -> username.equals(u.username))
                .orElse(null);
    }

    public User findById(Object id) {
        if (!noMongo) {
            return User.findById(id);
        }

        return readFromFile(User.class, COLLECTION, id.toString());
    }

    public List<User> listAll() {
        if (!noMongo) {
            return User.listAll();
        }

        return readAllFromFiles(User.class, COLLECTION);
    }

    public void persist(User user) {
        if (!noMongo) {
            user.persist();
            return;
        }

        if (user.id == null) {
            user.id = new org.bson.types.ObjectId();
        }
        writeToFile(user, COLLECTION, user.id.toString());
    }

    public void deleteByUsername(String username) {
        if (!noMongo) {
            User.delete("username", username);
            return;
        }

        User user = findByUsername(username);
        if (user != null && user.id != null) {
            deleteFile(COLLECTION, user.id.toString());
        }
    }
}
