package com.pawhaven.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pawhaven.dao.PawHavenDao;
import com.pawhaven.security.PasswordHasher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ApiServlet extends HttpServlet {
    private final ObjectMapper json = new ObjectMapper();
    private final PawHavenDao dao = new PawHavenDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = path(request);
        try {
            if (path.startsWith("/dashboard/")) {
                String role = path.substring("/dashboard/".length());
                if (!List.of("admin", "shelter", "user").contains(role)) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Dashboard not found.");
                    return;
                }
                if (!authorizeRole(request, response, role)) {
                    return;
                }
                Map<?, ?> user = (Map<?, ?>) request.getSession(false).getAttribute("user");
                int userId = ((Number) user.get("id")).intValue();
                Object data = role.equals("user")
                    ? dao.getUserDashboardData(userId)
                        : dao.getDashboardData(role.equals("admin"), userId);
                sendJson(response, HttpServletResponse.SC_OK, data);
                return;
            }
            if (path.equals("/pets")) {
                sendJson(response, HttpServletResponse.SC_OK, dao.listPets(
                        parameter(request, "search"), parameter(request, "species"),
                        parameter(request, "breed"), parameter(request, "gender"),
                        parameter(request, "location")));
                return;
            }
            if (path.startsWith("/pets/")) {
                int id = Integer.parseInt(path.substring("/pets/".length()));
                Map<String, Object> pet = dao.findPet(id);
                if (pet == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Pet not found.");
                } else {
                    sendJson(response, HttpServletResponse.SC_OK, pet);
                }
                return;
            }
            if (path.equals("/auth/me")) {
                HttpSession session = request.getSession(false);
                Object user = session == null ? null : session.getAttribute("user");
                if (user == null) {
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in.");
                } else {
                    sendJson(response, HttpServletResponse.SC_OK, user);
                }
                return;
            }
            if (path.startsWith("/uploads/")) {
                serveUpload(path.substring("/uploads/".length()), response);
                return;
            }
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "API route not found.");
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid pet id.");
        } catch (SQLException exception) {
            log("Database request failed", exception);
            sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The database is unavailable. Check the server configuration.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String path = path(request);
        try {
            switch (path) {
                case "/auth/register" -> register(request, response);
                case "/auth/login" -> login(request, response);
                case "/auth/logout" -> logout(request, response);
                case "/adoption-requests" -> createAdoption(request, response);
                case "/rescue-reports" -> createRescueReport(request, response);
                case "/contact-messages" -> createContactMessage(request, response);
                case "/shelter/pets" -> createShelterPet(request, response);
                default -> manageDashboard(request, response, path);
            }
        } catch (BadRequestException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (SQLException exception) {
            log("Database request failed", exception);
            if ("23505".equals(exception.getSQLState())) {
                sendError(response, HttpServletResponse.SC_CONFLICT,
                        "An account with this email already exists.");
            } else {
                sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "The database is unavailable. Check the server configuration.");
            }
        } catch (GeneralSecurityException exception) {
            log("Password operation failed", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process the account request.");
        }
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, GeneralSecurityException {
        JsonNode body = readJson(request);
        String name = minimumLength(required(body, "name", 120), "Name", 3);
        String email = email(required(body, "email", 254));
        String phone = phone(required(body, "phone", 20));
        String password = required(body, "password", 200);
        String role = required(body, "role", 20);
        if (!password.matches(".{6,}")) {
            throw new BadRequestException("Password must contain at least 6 characters.");
        }
        if (!role.equals("user") && !role.equals("shelter")) {
            throw new BadRequestException("Choose a valid account type.");
        }
        int id = dao.createUser(name, email, phone, PasswordHasher.hash(password), role);
        Map<String, Object> user = publicUser(id, name, email, role);
        request.getSession(true).setAttribute("user", user);
        sendJson(response, HttpServletResponse.SC_CREATED, user);
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, GeneralSecurityException {
        JsonNode body = readJson(request);
        String email = email(required(body, "email", 254));
        String password = required(body, "password", 200);
        Map<String, Object> stored = dao.findUserByEmail(email);
        if (stored == null || !PasswordHasher.verify(password,
                String.valueOf(stored.get("passwordHash")))) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Email or password is incorrect.");
            return;
        }
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        Map<String, Object> user = publicUser((Integer) stored.get("id"),
                (String) stored.get("name"), email, (String) stored.get("role"));
        request.getSession(true).setAttribute("user", user);
        sendJson(response, HttpServletResponse.SC_OK, user);
    }

    private void logout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sendJson(response, HttpServletResponse.SC_OK, Map.of("message", "Logged out."));
    }

    private void createAdoption(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        JsonNode body = readJson(request);
        Integer petId = optionalInteger(body, "petId");
        if (petId != null) {
            Map<String, Object> pet = dao.findPet(petId);
            if (pet == null || !"Available".equals(pet.get("status"))) {
                throw new BadRequestException("This pet is not available for adoption.");
            }
        }
        String preferredDate = optional(body, "preferredDate");
        if (!preferredDate.isBlank()) {
            try {
                LocalDate.parse(preferredDate);
            } catch (DateTimeParseException exception) {
                throw new BadRequestException("Enter a valid preferred adoption date.");
            }
        }
        String housingType = required(body, "housingType", 80);
        if (!List.of("Independent House", "Apartment", "Other").contains(housingType)) {
            throw new BadRequestException("Choose a valid housing type.");
        }
        String otherPets = choice(body, "hasOtherPets", "Yes", "No");
        String children = choice(body, "hasChildren", "Yes", "No");
        String applicantEmail = email(required(body, "applicantEmail", 254));
        int id = dao.createAdoptionRequest(petId, authenticatedUserId(request, applicantEmail, "user"),
            minimumLength(required(body, "applicantName", 120), "Name", 3),
            applicantEmail, phone(required(body, "phone", 20)),
            preferredDate, housingType, otherPets, children,
            minimumLength(required(body, "reason", 4000), "Reason", 10));
        sendJson(response, HttpServletResponse.SC_CREATED, Map.of("id", id));
    }

    private void createRescueReport(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, ServletException {
        JsonNode body = isMultipart(request) ? null : readJson(request);
        String animalType = formOrJson(request, body, "animalType");
        String location = formOrJson(request, body, "location");
        String emergency = formOrJson(request, body, "emergencyLevel");
        String condition = formOrJson(request, body, "condition");
        String description = formOrJson(request, body, "description");
        String reporterName = formOrJson(request, body, "reporterName");
        String reporterPhone = formOrJson(request, body, "reporterPhone");
        require(animalType, "Animal type", 30);
        if (!List.of("Dog", "Cat", "Other").contains(animalType)) {
            throw new BadRequestException("Choose a valid animal type.");
        }
        require(location, "Location", 250);
        if (!List.of("Low", "Medium", "High").contains(emergency)) {
            throw new BadRequestException("Choose a valid emergency level.");
        }
        if (!List.of("Injured", "Abandoned", "Lost", "Sick", "Other").contains(condition)) {
            throw new BadRequestException("Choose a valid animal condition.");
        }
        minimumLength(require(description, "Description", 4000), "Description", 10);
        minimumLength(require(reporterName, "Name", 120), "Name", 3);
        phone(reporterPhone);
        String imageUrl = saveRescueImage(request);
        int id = dao.createRescueReport(animalType, location, emergency, condition,
            description, reporterName, reporterPhone,
            authenticatedUserId(request, null, "user"), imageUrl);
        sendJson(response, HttpServletResponse.SC_CREATED, Map.of("id", id));
    }

    private void createContactMessage(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        JsonNode body = readJson(request);
        int id = dao.createContactMessage(minimumLength(required(body, "name", 120), "Name", 3),
                email(required(body, "email", 254)), required(body, "subject", 120),
            minimumLength(required(body, "message", 4000), "Message", 10));
        sendJson(response, HttpServletResponse.SC_CREATED, Map.of("id", id));
    }

    private String saveRescueImage(HttpServletRequest request) throws IOException, ServletException {
        if (!isMultipart(request)) {
            return null;
        }
        Part image = request.getPart("image");
        if (image == null || image.getSize() == 0) {
            return null;
        }
        String extension = switch (image.getContentType() == null ? "" : image.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw new BadRequestException("Upload a JPG, PNG, WEBP, or GIF image.");
        };
        String filename = UUID.randomUUID() + extension;
        Path directory = Path.of(System.getProperty("catalina.base"), "pawhaven-uploads");
        Files.createDirectories(directory);
        try (var input = image.getInputStream()) {
            Files.copy(input, directory.resolve(filename));
        }
        return "api/uploads/" + filename;
    }

    private void serveUpload(String filename, HttpServletResponse response) throws IOException {
        if (!filename.matches("[a-f0-9-]{36}\\.(jpg|png|webp|gif)")) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Image not found.");
            return;
        }
        Path directory = Path.of(System.getProperty("catalina.base"), "pawhaven-uploads").normalize();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Image not found.");
            return;
        }
        String type = switch (filename.substring(filename.lastIndexOf('.') + 1)) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/gif";
        };
        response.setContentType(type);
        response.setHeader("X-Content-Type-Options", "nosniff");
        Files.copy(file, response.getOutputStream());
    }

    private JsonNode readJson(HttpServletRequest request) throws IOException {
        JsonNode body;
        try {
            body = json.readTree(request.getInputStream());
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Request body must contain valid JSON.");
        }
        if (body == null || !body.isObject()) {
            throw new BadRequestException("A JSON object is required.");
        }
        return body;
    }

    private String formOrJson(HttpServletRequest request, JsonNode body, String key) {
        if (isMultipart(request)) {
            return request.getParameter(key) == null ? "" : request.getParameter(key).trim();
        }
        return optional(body, key);
    }

    private boolean isMultipart(HttpServletRequest request) {
        return request.getContentType() != null
                && request.getContentType().toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
    }

    private String required(JsonNode body, String key, int maxLength) {
        JsonNode value = body.get(key);
        return require(value == null || value.isNull() ? "" : value.asText().trim(), key, maxLength);
    }

    private String require(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required.");
        }
        if (value.length() > maxLength) {
            throw new BadRequestException(label + " is too long.");
        }
        return value.trim();
    }

    private String optional(JsonNode body, String key) {
        JsonNode value = body.get(key);
        return value == null || value.isNull() ? "" : value.asText().trim();
    }

    private String optionalBounded(JsonNode body, String key, int maxLength) {
        String value = optional(body, key);
        if (value.length() > maxLength) {
            throw new BadRequestException(key + " is too long.");
        }
        return value;
    }

    private Integer optionalInteger(JsonNode body, String key) {
        String value = optional(body, key);
        if (value.isBlank() || value.equals("null")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid pet id.");
        }
    }

    private String email(String value) {
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("Enter a valid email address.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String phone(String value) {
        if (!value.matches("[0-9]{10}")) {
            throw new BadRequestException("Enter a valid 10-digit phone number.");
        }
        return value;
    }

    private String minimumLength(String value, String label, int minimum) {
        if (value.length() < minimum) {
            throw new BadRequestException(label + " must contain at least " + minimum + " characters.");
        }
        return value;
    }

    private String choice(JsonNode body, String key, String first, String second) {
        String value = required(body, key, 10);
        if (!value.equals(first) && !value.equals(second)) {
            throw new BadRequestException("Choose a valid value for " + key + ".");
        }
        return value;
    }

    private String parameter(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? "" : value.trim();
    }

    private String path(HttpServletRequest request) {
        return request.getPathInfo() == null ? "/" : request.getPathInfo();
    }

    private boolean authorizeRole(HttpServletRequest request, HttpServletResponse response,
                                  String requiredRole) throws IOException {
        Map<?, ?> user = sessionUser(request);
        if (user == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in.");
            return false;
        }
        if (!requiredRole.equals(user.get("role"))) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "You do not have access to this dashboard.");
            return false;
        }
        return true;
    }

    private Map<?, ?> sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("user");
        return value instanceof Map<?, ?> user ? user : null;
    }

    private Integer authenticatedUserId(HttpServletRequest request, String email, String role) {
        Map<?, ?> user = sessionUser(request);
        if (user == null || !role.equals(user.get("role"))) {
            return null;
        }
        if (email != null && !email.equals(user.get("email"))) {
            return null;
        }
        return ((Number) user.get("id")).intValue();
    }

    private void createShelterPet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        if (!authorizeRole(request, response, "shelter")) {
            return;
        }
        Map<?, ?> user = sessionUser(request);
        JsonNode body = readJson(request);
        String species = required(body, "species", 30);
        String gender = required(body, "gender", 20);
        if (!List.of("Dog", "Cat", "Other").contains(species)) {
            throw new BadRequestException("Choose a valid species.");
        }
        if (!List.of("Male", "Female", "Unknown").contains(gender)) {
            throw new BadRequestException("Choose a valid gender.");
        }
        int age = requiredInteger(body, "age");
        if (age < 0 || age > 40) {
            throw new BadRequestException("Age must be between 0 and 40 years.");
        }
        String image = optionalBounded(body, "image", 500);
        if (image.isBlank()) {
            image = species.equals("Cat") ? "images/cat1.jpg" : "images/dog1.jpg";
        }
        int id = dao.createPet(((Number) user.get("id")).intValue(),
                minimumLength(required(body, "name", 100), "Name", 2), species,
                required(body, "breed", 100), age, gender, required(body, "location", 120),
            optionalBounded(body, "health", 120).isBlank()
                ? "Unknown" : required(body, "health", 120),
                booleanValue(body, "vaccinated"), booleanValue(body, "dewormed"),
            image, optionalBounded(body, "description", 4000));
        sendJson(response, HttpServletResponse.SC_CREATED, Map.of("id", id));
    }

    private void manageDashboard(HttpServletRequest request, HttpServletResponse response,
                                 String path) throws IOException, SQLException {
        String[] parts = path.split("/");
        if (parts.length != 5 || !parts[1].equals("dashboard")) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "API route not found.");
            return;
        }
        int recordId;
        try {
            recordId = Integer.parseInt(parts[3]);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid record id.");
        }
        String resource = parts[2];
        String action = parts[4];
        JsonNode body = readJson(request);
        Map<?, ?> user = sessionUser(request);
        String role = user == null ? "" : String.valueOf(user.get("role"));
        int userId = user == null ? 0 : ((Number) user.get("id")).intValue();

        if (resource.equals("adoptions") && action.equals("status")) {
            if (!authorizeAnyRole(request, response, "admin", "shelter")) {
                return;
            }
            String status = required(body, "status", 20);
            if (!List.of("Pending", "Approved", "Rejected").contains(status)) {
                throw new BadRequestException("Choose a valid application status.");
            }
            if (!dao.updateAdoptionStatus(recordId, status, role.equals("admin"), userId)) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND,
                        "Application not found, outside your shelter, or pet no longer available.");
                return;
            }
            sendJson(response, HttpServletResponse.SC_OK, Map.of("message", "Application updated."));
            return;
        }
        if (resource.equals("rescues") && action.equals("status")) {
            if (!authorizeAnyRole(request, response, "admin", "shelter")) {
                return;
            }
            String status = required(body, "status", 20);
            if (!List.of("Open", "In Progress", "Resolved").contains(status)) {
                throw new BadRequestException("Choose a valid rescue status.");
            }
            if (!dao.updateRescueStatus(recordId, status, role.equals("admin"), userId)) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Rescue report not found.");
                return;
            }
            sendJson(response, HttpServletResponse.SC_OK, Map.of("message", "Rescue report updated."));
            return;
        }
        if (resource.equals("rescues") && action.equals("assign")) {
            if (!authorizeRole(request, response, "admin")) {
                return;
            }
            int shelterId = requiredInteger(body, "shelterId");
            if (!dao.assignRescueToShelter(recordId, shelterId)) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND,
                        "Rescue report or shelter not found.");
                return;
            }
            sendJson(response, HttpServletResponse.SC_OK, Map.of("message", "Rescue report assigned."));
            return;
        }
        if (resource.equals("pets") && action.equals("assign")) {
            if (!authorizeRole(request, response, "admin")) {
                return;
            }
            int shelterId = requiredInteger(body, "shelterId");
            if (!dao.assignPetToShelter(recordId, shelterId)) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Pet or shelter not found.");
                return;
            }
            sendJson(response, HttpServletResponse.SC_OK, Map.of("message", "Pet assigned."));
            return;
        }
        sendError(response, HttpServletResponse.SC_NOT_FOUND, "API route not found.");
    }

    private boolean authorizeAnyRole(HttpServletRequest request, HttpServletResponse response,
                                     String... roles) throws IOException {
        Map<?, ?> user = sessionUser(request);
        if (user == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in.");
            return false;
        }
        for (String role : roles) {
            if (role.equals(user.get("role"))) {
                return true;
            }
        }
        sendError(response, HttpServletResponse.SC_FORBIDDEN,
                "You do not have access to this operation.");
        return false;
    }

    private int requiredInteger(JsonNode body, String key) {
        JsonNode value = body.get(key);
        if (value == null || !value.canConvertToInt()) {
            throw new BadRequestException(key + " must be an integer.");
        }
        return value.intValue();
    }

    private boolean booleanValue(JsonNode body, String key) {
        JsonNode value = body.get(key);
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw new BadRequestException(key + " must be true or false.");
        }
        return value.booleanValue();
    }

    private Map<String, Object> publicUser(int id, String name, String email, String role) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("name", name);
        user.put("email", email);
        user.put("role", role);
        return user;
    }

    private void sendJson(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), value);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        sendJson(response, status, Map.of("error", message));
    }

    private static class BadRequestException extends RuntimeException {
        private BadRequestException(String message) {
            super(message);
        }
    }
}