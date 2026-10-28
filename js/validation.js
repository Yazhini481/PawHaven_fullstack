document.addEventListener("DOMContentLoaded", function () {

    const registerForm = document.getElementById("registerForm");

    if (registerForm) {
        registerForm.addEventListener("submit", validateRegistration);
    }

    const loginForm = document.getElementById("loginForm");

    if (loginForm) {
        loginForm.addEventListener("submit", validateLogin);
    }

    const adoptionForm = document.getElementById("adoptionForm");

    if (adoptionForm) {
        adoptionForm.addEventListener("submit", validateAdoption);
    }

    const rescueForm = document.getElementById("rescueForm");

    if (rescueForm) {
        rescueForm.addEventListener("submit", validateRescue);
    }

    const contactForm = document.getElementById("contactForm");

    if (contactForm) {
        contactForm.addEventListener("submit", validateContact);
    }
});


function showError(id, message) {

    const element = document.getElementById(id);

    if (element) {
        element.textContent = message;
    }
}


function clearErrors() {

    document.querySelectorAll(".error-message")
        .forEach(function (element) {
            element.textContent = "";
        });
}


function validateRegistration(event) {

    event.preventDefault();

    clearErrors();

    const name =
        document.getElementById("name").value.trim();

    const email =
        document.getElementById("email").value.trim();

    const phone =
        document.getElementById("phone").value.trim();

    const password =
        document.getElementById("password").value;

    const confirmPassword =
        document.getElementById("confirmPassword").value;

    let valid = true;

    if (name.length < 3) {
        showError("nameError", "Enter a valid name.");
        valid = false;
    }

    if (!email.includes("@")) {
        showError("emailError", "Enter a valid email.");
        valid = false;
    }

    if (!/^[0-9]{10}$/.test(phone)) {
        showError("phoneError", "Enter a valid 10-digit phone number.");
        valid = false;
    }

    if (password.length < 6) {
        showError("passwordError",
            "Password must contain at least 6 characters.");
        valid = false;
    }

    if (password !== confirmPassword) {
        showError("confirmPasswordError",
            "Passwords do not match.");
        valid = false;
    }

    if (valid) {
        submitForm("api/auth/register", {
            name: name,
            email: email,
            phone: phone,
            password: password,
            role: document.getElementById("accountType").value
        }, "successMessage", event.target);
    }
}


function validateLogin(event) {

    event.preventDefault();

    clearErrors();

    const email =
        document.getElementById("loginEmail").value.trim();

    const password =
        document.getElementById("loginPassword").value;

    let valid = true;

    if (!email.includes("@")) {
        showError("loginEmailError", "Enter a valid email.");
        valid = false;
    }

    if (password.length < 6) {
        showError("loginPasswordError",
            "Password must contain at least 6 characters.");
        valid = false;
    }

    if (valid) {
        submitForm("api/auth/login", {
            email: email,
            password: password
        }, null, event.target, null, "loginPasswordError").then(function (result) {
            if (result) {
                window.location.href = result.role === "admin"
                    ? "admin-dashboard.html"
                    : result.role === "shelter"
                        ? "shelter-dashboard.html"
                        : "dashboard.html";
            }
        }).catch(function (error) {
            showError("loginPasswordError", error.message);
        });
    }
}


function validateAdoption(event) {

    event.preventDefault();

    clearErrors();

    const name =
        document.getElementById("applicantName").value.trim();

    const email =
        document.getElementById("applicantEmail").value.trim();

    const reason =
        document.getElementById("reason").value.trim();

    const agreement =
        document.getElementById("agreement").checked;

    let valid = true;

    if (name.length < 3) {
        showError("applicantNameError", "Enter your name.");
        valid = false;
    }

    if (!email.includes("@")) {
        showError("applicantEmailError",
            "Enter a valid email.");
        valid = false;
    }

    if (reason.length < 10) {
        showError("reasonError",
            "Please provide more details.");
        valid = false;
    }

    if (!agreement) {
        showError("agreementError",
            "Please accept the adoption agreement.");
        valid = false;
    }

    if (valid) {

        submitForm("api/adoption-requests", {
            petId: new URLSearchParams(window.location.search).get("id"),
            applicantName: name,
            applicantEmail: email,
            phone: document.getElementById("applicantPhone").value.trim(),
            preferredDate: document.getElementById("preferredDate").value,
            housingType: document.getElementById("housingType").value,
            hasOtherPets: document.querySelector("input[name='otherPets']:checked")?.value,
            hasChildren: document.querySelector("input[name='children']:checked")?.value,
            reason: reason
        }, "adoptionSuccess", event.target,
        "Your adoption application has been submitted successfully.");
    }
}


function validateRescue(event) {

    event.preventDefault();

    clearErrors();

    const animal =
        document.getElementById("animalType").value;

    const location =
        document.getElementById("rescueLocation").value.trim();

    const description =
        document.getElementById("rescueDescription").value.trim();

    let valid = true;

    if (animal === "") {
        showError("animalTypeError",
            "Select the animal type.");
        valid = false;
    }

    if (location.length < 3) {
        showError("locationError",
            "Enter the rescue location.");
        valid = false;
    }

    if (description.length < 10) {
        showError("descriptionError",
            "Provide more information.");
        valid = false;
    }

    if (valid) {

        const report = new FormData();
        report.append("animalType", animal);
        report.append("location", location);
        report.append("emergencyLevel", document.querySelector("input[name='emergency']:checked")?.value || "");
        report.append("condition", document.getElementById("condition").value);
        report.append("description", description);
        report.append("reporterName", document.getElementById("reporterName").value.trim());
        report.append("reporterPhone", document.getElementById("reporterPhone").value.trim());
        const image = document.getElementById("rescueImage").files[0];
        if (image) {
            report.append("image", image);
        }
        submitForm("api/rescue-reports", report, "rescueSuccess", event.target,
            "Rescue request submitted successfully.");
    }
}


function validateContact(event) {

    event.preventDefault();

    clearErrors();

    const name =
        document.getElementById("contactName").value.trim();

    const email =
        document.getElementById("contactEmail").value.trim();

    const message =
        document.getElementById("contactMessage").value.trim();

    let valid = true;

    if (name.length < 3) {
        showError("contactNameError", "Enter your name.");
        valid = false;
    }

    if (!email.includes("@")) {
        showError("contactEmailError",
            "Enter a valid email.");
        valid = false;
    }

    if (message.length < 10) {
        showError("contactMessageError",
            "Please enter your message.");
        valid = false;
    }

    if (valid) {

        submitForm("api/contact-messages", {
            name: name,
            email: email,
            subject: document.getElementById("contactSubject").value,
            message: message
        }, "contactSuccess", event.target,
        "Thank you! Your message has been sent.");
    }
}


async function submitForm(endpoint, data, successId, form, successText, errorId) {
    try {
        const options = {
            method: "POST",
            body: data instanceof FormData ? data : JSON.stringify(data)
        };
        if (!(data instanceof FormData)) {
            options.headers = { "Content-Type": "application/json" };
        }
        const response = await fetch(endpoint, options);
        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.error || "The request could not be completed.");
        }

        if (successId) {
            const message = document.getElementById(successId);
            message.classList.remove("form-error");
            message.textContent = successText || "Registration successful!";
            message.style.display = "block";
        }
        form.reset();
        return result;
    } catch (error) {
        if (successId) {
            const message = document.getElementById(successId);
            message.classList.add("form-error");
            message.textContent = error.message;
            message.style.display = "block";
        }
        if (errorId) {
            showError(errorId, error.message);
        }
        return null;
    }
}