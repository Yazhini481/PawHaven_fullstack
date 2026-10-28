const petDetails = document.getElementById("petDetails");
const petId = new URLSearchParams(window.location.search).get("id");

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, function (character) {
        return {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;"
        }[character];
    });
}

async function loadPetDetails() {
    try {
        const response = await fetch("api/pets/" + encodeURIComponent(petId || ""));
        const pet = await response.json();
        if (!response.ok) {
            throw new Error(pet.error || "Pet information not found.");
        }

        const name = escapeHtml(pet.name);
        petDetails.innerHTML = `
            <div class="pet-profile">
                <div class="pet-profile-image"><img src="${escapeHtml(pet.image)}" alt="${name}"></div>
                <div class="pet-profile-info">
                    <h1>${name}</h1>
                    <p class="pet-breed">${escapeHtml(pet.breed)}</p>
                    <div class="pet-basic-info">
                        <div><strong>Age</strong><span>${escapeHtml(pet.age)} years</span></div>
                        <div><strong>Gender</strong><span>${escapeHtml(pet.gender)}</span></div>
                        <div><strong>Location</strong><span>📍 ${escapeHtml(pet.location)}</span></div>
                    </div>
                    <h2>About ${name}</h2>
                    <p>${escapeHtml(pet.description)}</p>
                    <h2>Health Information</h2>
                    <div class="health-list">
                        <p>${pet.vaccinated ? "✓" : "✗"} Vaccinated</p>
                        <p>${pet.health === "Healthy" ? "✓" : "✗"} Healthy</p>
                        <p>${pet.dewormed ? "✓" : "✗"} Dewormed</p>
                    </div>
                    ${pet.status === "Available"
                        ? `<a href="adoption.html?id=${encodeURIComponent(pet.id)}" class="btn btn-primary">❤️ Adopt ${name}</a>`
                        : `<p class="status adopted">Already adopted</p>`}
                    <a href="adopt.html" class="btn btn-outline">← Back to Pets</a>
                </div>
            </div>`;
    } catch (error) {
        petDetails.innerHTML = `
            <div class="pet-not-found">
                <h2>Pet Information Not Found 🐾</h2>
                <p>${escapeHtml(error.message)}</p>
                <a href="adopt.html" class="btn btn-primary">Back to Adopt</a>
            </div>`;
    }
}

loadPetDetails();