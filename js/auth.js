function showPassword() {

    const password =
        document.getElementById("password");

    const confirmPassword =
        document.getElementById("confirmPassword");

    if (password) {
        password.type =
            password.type === "password"
                ? "text"
                : "password";
    }

    if (confirmPassword) {
        confirmPassword.type =
            confirmPassword.type === "password"
                ? "text"
                : "password";
    }
}