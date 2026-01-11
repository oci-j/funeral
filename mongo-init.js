// MongoDB initialization script for Funeral OCI Registry
db = db.getSiblingDB('oci_registry');

// Create collections
db.createCollection('repositories');
db.createCollection('manifests');
db.createCollection('blobs');
db.createCollection('users');

// Create indexes for better performance
db.repositories.createIndex({ "name": 1 }, { unique: true });
db.manifests.createIndex({ "repositoryName": 1, "digest": 1 });
db.manifests.createIndex({ "repositoryName": 1, "tag": 1 });
db.manifests.createIndex({ "repositoryName": 1, "updatedAt": -1 });
db.blobs.createIndex({ "digest": 1 }, { unique: true });
db.users.createIndex({ "username": 1 }, { unique: true });

print('Funeral OCI Registry - MongoDB initialized successfully');
