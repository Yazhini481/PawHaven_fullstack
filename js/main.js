document.addEventListener("DOMContentLoaded", function () {

    // Mobile navigation
    const menuToggle = document.getElementById("menuToggle");
    const navLinks = document.getElementById("navLinks");

    if (menuToggle && navLinks) {
        menuToggle.addEventListener("click", function () {
            navLinks.classList.toggle("show");
        });
    }

    // Favorite buttons
    document.querySelectorAll(".favorite").forEach(function (button) {

        button.addEventListener("click", function () {

            this.classList.toggle("active");

            if (this.classList.contains("active")) {
                this.textContent = "♥";
            } else {
                this.textContent = "♡";
            }
        });
    });

    // Dynamic year
    const year = document.getElementById("year");

    if (year) {
        year.textContent = new Date().getFullYear();
    }
});