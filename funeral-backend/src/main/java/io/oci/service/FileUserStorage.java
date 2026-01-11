package io.oci.service;

import io.oci.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Named("file-user-storage")
public class FileUserStorage implements UserStorage {

    private final String COLLECTION = "users";

    @Inject
    FileStorageBase fileStorage;

    @Override
    public User findByUsername(String username) {
        return fileStorage.readAllFromFiles(User.class, COLLECTION).stream()
                .filter(u -> username.equals(u.username))
                .findFirst()
                .orElse(null);
    }

    @Override
    public User findById(Object id) {
        return fileStorage.readFromFile(User.class, COLLECTION, id.toString());
    }

    @Override
    public List<User> listAll() {
        return fileStorage.readAllFromFiles(User.class, COLLECTION);
    }

    @Override
    public void persist(User user) {
        if (user.id == null) {
            user.id = new org.bson.types.ObjectId();
        }
        fileStorage.writeToFile(user, COLLECTION, user.id.toString());
    }

    @Override
    public void deleteByUsername(String username) {
        User user = findByUsername(username);
        if (user != null && user.id != null) {
            fileStorage.deleteFile(COLLECTION, user.id.toString());
        }
    }
}
