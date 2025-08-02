FUNERAL is an oci image registry following [oci-distribution-spec](https://github.com/opencontainers/distribution-spec.git)

In java.

# Current Status

It just begins the development.

Don't use it in production, it's not ready yet.

It passed only 59 / 79 of the tests defined
in [oci-distribution-spec](https://github.com/opencontainers/distribution-spec.git)

```
Ran 74 of 79 Specs in 3.401 seconds
FAIL! -- 59 Passed | 15 Failed | 0 Pending | 5 Skipped
--- FAIL: TestConformance (3.41s)
FAIL
```

Due to implementation reason (quarkus), It only supports single-part name image for now, like `node:latest`, but not
`bitnami/nginx:latest`, because a slash be in the name.
(This will be fixed but just not ready for now.)

# Usage

ignore the funeral-frontend folder.

use funeral-backend folder, it is a quarkus maven project, you can use it as `mvn quarkus:dev`

modify the resources/application.yml to config minio & mongo connection & other configs

# Short-Term Goal

This repo is a subject of a bigger project to make oci better, with less bandwidth.
[Add an import mechanism to oci image format, to reduce bandwidth cost on image upgrades]https://github.com/users/XenoAmess/projects/1

The main efforts now be on:

1. make it pass more tests, means
   follow [oci-distribution-spec](https://github.com/opencontainers/distribution-spec.git) spec more.

   according to docs in [oci-distribution-spec](https://github.com/opencontainers/distribution-spec.git),
    ```
    The tests are broken down into 4 major categories:
    
    1. Pull - Highest priority - All OCI registries MUST support pulling OCI container
    images.
    2. Push - Registries need a way to get content to be pulled, but clients can/should
    be more forgiving here. For example, if needing to fallback after an unsupported endpoint.
    3. Content Discovery - Includes tag listing (and possibly search in the future).
    4. Content Management - Lowest Priority - Includes tag, blob, and repo deletion.
    (Note: Many registries may have other ways to accomplish this than the OCI API.)
    ```

   we would focus on Pull and Push part.

2. make it usable enough for my `image import block` research, then use it.

# Ultimate Goal

The ultimate goal for the repo is to be used as a light-weighted image registry.

It would focus more on usability, and become very easy to use/establish.

1. make middlewares optional

   right now we use mongo for data storage, and minio for blob storage.
   we have a plan to offer some configs to allow using disk instead, for people who don't want to setup docker and
   minio.

2. more light-weighted

   we will find a way to make binary release, most likely by graalvm.

3. stability refines

4. storage compression

   After my research of bandwidth/image compression done (I hope I can finish it in 2 years, but I doubt. too many
   things to do...),
   would I start another project to make the storage more compressed...by replacing the TAR.

   But that would be a long-term goal, and we shall do things one by one.

5. this repo doesn't focus on security, so it won't support things like
    - image signing
    - image scanning
    - etc.

   It be based on the assumption that the image registry is used in a trusted environment, like a private network.
   At least any people who have log in access to the FUNERAL do not use any malicious images/clients.
   (And this is the case for my research and work, so I don't care much about security for now.)
   Though pull requests be welcomed if there be people who want to add security features.
