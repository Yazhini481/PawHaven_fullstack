let allPets = [];

document.addEventListener("DOMContentLoaded", function () {

    const petContainer = document.getElementById("petContainer");

    if (!petContainer) {
        return;
    }

    loadPets();

    const searchInput = document.getElementById("searchPet");
    const speciesFilter = document.getElementById("speciesFilter");
    const breedFilter = document.getElementById("breedFilter");
    const genderFilter = document.getElementById("genderFilter");
    const locationFilter = document.getElementById("locationFilter");

    [searchInput, speciesFilter, breedFilter, genderFilter, locationFilter]
        .forEach(function (element) {

            if (element) {
                element.addEventListener("input", filterPets);
                element.addEventListener("change", filterPets);
            }
        });
});


async function loadPets() {

    try {

        const response = await fetch("api/pets");

        if (!response.ok) {
            throw new Error("Unable to load pet data");
        }

        allPets = await response.json();

        displayPets(allPets);

    } catch (error) {

        console.error(error);

        const container = document.getElementById("petContainer");

        if (container) {
            container.innerHTML = `
                <div class="alert alert-error" style="display:block">
                    Unable to load pet information.
                </div>
            `;
        }
    }
}


function displayPets(pets) {

    const container = document.getElementById("petContainer");

    if (!container) {
        return;
    }

    container.innerHTML = "";

    if (pets.length === 0) {

        container.innerHTML = `
            <div class="alert alert-error" style="display:block">
                No pets found matching your search.
            </div>
        `;

        return;
    }

    pets.forEach(function (pet) {
        const card = document.createElement("div");
        card.className = "pet-card";

        const imageContainer = document.createElement("div");
        imageContainer.className = "pet-image";
        const image = document.createElement("img");
        image.src = safePetImage(pet.image);
        image.alt = pet.name;
        imageContainer.appendChild(image);

        const favorite = document.createElement("button");
        favorite.className = "favorite";
        favorite.type = "button";
        favorite.setAttribute("aria-label", "Add " + pet.name + " to favorites");
        favorite.textContent = "♡";
        imageContainer.appendChild(favorite);
        card.appendChild(imageContainer);

        const info = document.createElement("div");
        info.className = "pet-info";
        const title = document.createElement("div");
        title.className = "pet-title";
        const name = document.createElement("h3");
        name.textContent = pet.name;
        title.appendChild(name);
        const status = document.createElement("span");
        status.className = "status";
        if (pet.status === "Adopted") {
            status.classList.add("adopted");
        }
        status.textContent = pet.status;
        title.appendChild(status);
        info.appendChild(title);

        const meta = document.createElement("p");
        meta.className = "pet-meta";
        meta.append(document.createTextNode(
            pet.breed + " • " + pet.age + " years • " + pet.gender));
        meta.appendChild(document.createElement("br"));
        meta.append(document.createTextNode("📍 " + pet.location));
        info.appendChild(meta);

        const profile = document.createElement("a");
        profile.className = "btn btn-primary";
        profile.href = "pet-details.html?id=" + encodeURIComponent(pet.id);
        profile.textContent = "View Profile";
        info.appendChild(profile);
        card.appendChild(info);

        container.appendChild(card);
    });

    // Add favorite event listeners
    document.querySelectorAll(".favorite").forEach(function (button) {

        button.addEventListener("click", function () {

            this.classList.toggle("active");

            this.textContent =
                this.classList.contains("active") ? "♥" : "♡";
        });
    });
}


function filterPets() {

    const search =
        document.getElementById("searchPet")?.value.toLowerCase() || "";

    const species =
        document.getElementById("speciesFilter")?.value || "";

    const breed =
        document.getElementById("breedFilter")?.value || "";

    const gender =
        document.getElementById("genderFilter")?.value || "";

    const location =
        document.getElementById("locationFilter")?.value || "";

    const filteredPets = allPets.filter(function (pet) {

        const matchesSearch =
            pet.name.toLowerCase().includes(search) ||
            pet.breed.toLowerCase().includes(search);

        const matchesSpecies =
            species === "" || pet.species === species;

        const matchesBreed =
            breed === "" || pet.breed === breed;

        const matchesGender =
            gender === "" || pet.gender === gender;

        const matchesLocation =
            location === "" || pet.location === location;

        return matchesSearch &&
               matchesSpecies &&
               matchesBreed &&
               matchesGender &&
               matchesLocation;
    });

    displayPets(filteredPets);
}


function safePetImage(value) {
    try {
        const url = new URL(value, window.location.href);
        return ["http:", "https:"].includes(url.protocol) ? url.href : "";
    } catch (error) {
        return "";
    }
}