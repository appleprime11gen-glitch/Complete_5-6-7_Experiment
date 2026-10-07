# Deploy to Render

Create a Render Web Service with the Docker runtime and set `postapi` as its Root Directory. Render builds the `Dockerfile` in that directory. Leave the start command empty so the image entrypoint runs. The image uses Java 17, activates the `prod` profile, binds to `0.0.0.0`, and listens on Render's `PORT` (defaulting to 8080 locally).

Configure these variables in the Render service's Environment settings:

| Name | Value |
| --- | --- |
| `MONGODB_URI` | MongoDB Atlas connection URI, including the intended database name. Configure Atlas network access to allow the Render service to connect. |
| `JWT_SECRET_BASE64` | Persistent secret Base64 value decoding to at least 32 random bytes. |
| `APP_AES_KEY_BASE64` | A different persistent secret Base64 value decoding to exactly 32 random bytes. Changing this key without migrating stored encrypted credentials makes them unreadable. |
| `FRONTEND_ORIGINS` | Comma-separated exact browser origins, with no paths or trailing slash. For the frontend served by this service, use `https://<service-name>.onrender.com`. This allowlist controls both CORS and cookie CSRF validation. |
| `AUTH_COOKIE_SAME_SITE` | `Strict` when frontend and API are same-site. For a truly cross-site frontend, use `None`; production cookies remain Secure. |

The production profile enforces `AUTH_COOKIE_SECURE=true`, requires the Mongo URI, trusted origins, and both keys, and disables development-admin bootstrap. The Docker image already sets `SPRING_PROFILES_ACTIVE=prod`; do not override it with `dev`. Keep the JWT and AES values stable across deploys. Store actual values only in Render's environment settings; do not commit them or place them in `.env.example`.

The static frontend already uses relative `/api/...` URLs and can be served by this same service.
