package com.isd.wms.controller;

import com.isd.wms.dto.user.UserCreateRequest;
import com.isd.wms.dto.user.UserResponse;
import com.isd.wms.dto.user.UserUpdateRequest;
import com.isd.wms.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing WMS users under the supervisor scope.
 *
 * <p>Provides endpoints for listing, registering, updating, and deleting users.
 * Access control is enforced at the service level; the controller maps
 * {@link AccessDeniedException} to {@code 403 Forbidden} and other exceptions
 * to appropriate error responses. Bean validation errors are handled locally
 * via {@link #handleValidationExceptions}.</p>
 *
 * <p>Base path: {@code /api/supervisor/users}</p>
 */
@RestController
@RequestMapping("/api/supervisor/users")
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Constructs a {@code UserController} with the given {@link UserService}.
     *
     * @param userService the service used to perform user management operations
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Retrieves all users registered in the system.
     *
     * @return {@code 200 OK} with a list of {@link UserResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Registers a new user account and sends a verification email.
     *
     * <p>Returns {@code 403 Forbidden} if the caller lacks the required permissions,
     * and {@code 409 Conflict} if a user with the same username or email already exists.</p>
     *
     * @param request the user creation request; must be valid
     * @return {@code 201 Created} with a confirmation body containing the username and role,
     *         or an error body on failure
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser( @Valid @RequestBody UserCreateRequest request) {
        try {
            userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User was successfully registered!",
                "username", request.username(),
                "role", request.userRole()
            ));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Updates an existing user's details.
     *
     * <p>Returns {@code 403 Forbidden} if the caller lacks the required permissions,
     * and {@code 400 Bad Request} for any other failure.</p>
     *
     * @param id      the ID of the user to update
     * @param request the update request containing the new user data; must be valid
     * @return {@code 200 OK} with a success message, or an error body on failure
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id,@Valid @RequestBody UserUpdateRequest request) {
        try {
            userService.updateUser(id, request);
            return ResponseEntity.ok(Map.of(
                "message", "User successfully updated!"
            ));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Deletes a user by their ID.
     *
     * <p>Returns {@code 403 Forbidden} if the caller lacks the required permissions,
     * and {@code 400 Bad Request} for any other failure.</p>
     *
     * @param id the ID of the user to delete
     * @return {@code 200 OK} with a success message, or an error body on failure
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        log.info("User deletion attempt for ID: {}", id);
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of(
                "message", "User successfully deleted!"
            ));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Deletion failed for user ID {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Handles bean validation failures ({@link MethodArgumentNotValidException}) for this controller.
     *
     * <p>Collects all field-level validation errors and returns them as a map of
     * field name to error message.</p>
     *
     * @param ex the exception thrown when request body validation fails
     * @return {@code 400 Bad Request} with a map of field names to their respective error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }


}
