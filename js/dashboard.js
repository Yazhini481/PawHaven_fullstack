document.addEventListener("DOMContentLoaded", function () {
    const role = document.body.dataset.dashboardRole;
    const message = document.getElementById("dashboardMessage");

    document.querySelector("[data-dashboard-logout]")?.addEventListener("click", async function (event) {
        event.preventDefault();
        try {
            await fetch("api/auth/logout", { method: "POST" });
        } catch (error) {
            console.error("Logout request failed", error);
        } finally {
            window.location.href = "index.html";
        }
    });

    document.addEventListener("click", function (event) {
        const button = event.target.closest("[data-dashboard-action]");
        if (button) {
            saveDashboardAction(button, role, message);
        }
    });

    document.getElementById("petForm")?.addEventListener("submit", function (event) {
        submitPet(event, message);
    });

    loadDashboard(role, message);
});

async function loadDashboard(role, message) {
    try {
        const response = await fetch("api/dashboard/" + encodeURIComponent(role));
        const result = await response.json();
        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }
        if (response.status === 403) {
            throw new Error("You do not have access to this dashboard.");
        }
        if (!response.ok) {
            throw new Error(result.error || "Unable to load dashboard data.");
        }

        const shelters = result.shelters || [];
        window.dashboardShelters = shelters;

        Object.entries(result.stats).forEach(function ([key, value]) {
            const target = document.querySelector(`[data-stat="${key}"]`);
            if (target) {
                target.textContent = value;
            }
        });
        renderAdoptions(result.adoptions, role);
        renderRescues(result.rescueReports, shelters, role);
        renderPets(result.pets || [], shelters, role);
        if (role === "admin") {
            renderTable("dashboardMessages", result.contactMessages || [],
                ["name", "email", "subject", "message", "created_at"], null, 5);
        }
        if (message) {
            message.textContent = "";
            message.classList.remove("error");
        }
    } catch (error) {
        if (message) {
            message.textContent = error.message;
            message.classList.add("error");
        }
    }
}

function renderAdoptions(rows, role) {
    const fields = role === "admin"
        ? ["applicant_name", "pet_name", "shelter_name"]
        : role === "user"
            ? ["pet_name", "created_at"]
            : ["applicant_name", "pet_name", "created_at"];
    renderTable("dashboardAdoptions", rows, fields, function (row, record) {
        if (role !== "user") {
            appendStatusControl(row, record, "adoptions", ["Pending", "Approved", "Rejected"]);
        } else {
            appendCell(row, record.status);
        }
    }, role === "user" ? 3 : fields.length + 2);
}

function renderRescues(rows, shelters, role) {
    if (role === "user") {
        renderTable("dashboardRescues", rows,
            ["animal_type", "location", "emergency_level"], function (row, record) {
                appendCell(row, record.status);
                appendCell(row, formatDate(record.created_at));
            }, 5);
        return;
    }
    renderTable("dashboardRescues", rows,
        ["reporter_name", "animal_type", "location", "emergency_level"], function (row, record) {
            appendStatusControl(row, record, "rescues", ["Open", "In Progress", "Resolved"]);
            if (role === "admin") {
                const cell = document.createElement("td");
                const shelterSelect = createShelterSelect(shelters, record.shelter_id);
                shelterSelect.dataset.shelterInput = "true";
                cell.appendChild(shelterSelect);
                cell.appendChild(actionButton("assign-rescue", record.id, "Assign"));
                row.appendChild(cell);
            }
        }, 6);
}

function renderPets(rows, shelters, role) {
    if (role === "user") {
        return;
    }
    if (role === "shelter") {
        renderTable("dashboardPets", rows, ["name", "species", "breed", "age", "status"], null, 5);
        return;
    }
    renderTable("dashboardPets", rows, ["name", "species", "breed", "status"], function (row, pet) {
        const shelterCell = document.createElement("td");
        const shelterSelect = createShelterSelect(shelters, pet.shelter_id);
        shelterSelect.dataset.shelterInput = "true";
        shelterCell.appendChild(shelterSelect);
        row.appendChild(shelterCell);
        const actionCell = document.createElement("td");
        actionCell.appendChild(actionButton("assign-pet", pet.id, "Assign"));
        row.appendChild(actionCell);
    }, 6);
}

function renderTable(bodyId, rows, fields, appendExtra, columnCount) {
    const body = document.getElementById(bodyId);
    if (!body) {
        return;
    }
    body.replaceChildren();
    if (!rows.length) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = columnCount;
        cell.textContent = "No records yet.";
        row.appendChild(cell);
        body.appendChild(row);
        return;
    }
    rows.forEach(function (record) {
        const row = document.createElement("tr");
        fields.forEach(function (field) {
            appendCell(row, field === "created_at" ? formatDate(record[field]) : record[field]);
        });
        if (appendExtra) {
            appendExtra(row, record);
        }
        body.appendChild(row);
    });
}

function appendCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = String(value ?? "");
    row.appendChild(cell);
}

function appendStatusControl(row, record, resource, statuses) {
    const statusCell = document.createElement("td");
    const status = document.createElement("span");
    status.className = "status";
    status.textContent = record.status || "";
    statusCell.appendChild(status);
    row.appendChild(statusCell);

    const actionCell = document.createElement("td");
    const select = document.createElement("select");
    select.className = "form-control dashboard-action-select";
    select.dataset.statusInput = "true";
    statuses.forEach(function (value) {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        option.selected = record.status === value;
        select.appendChild(option);
    });
    actionCell.appendChild(select);
    actionCell.appendChild(actionButton("save-status", record.id, "Save", resource));
    row.appendChild(actionCell);
}

function createShelterSelect(shelters, selectedId) {
    const select = document.createElement("select");
    select.className = "form-control dashboard-action-select";
    const prompt = document.createElement("option");
    prompt.value = "";
    prompt.textContent = "Select shelter";
    select.appendChild(prompt);
    shelters.forEach(function (shelter) {
        const option = document.createElement("option");
        option.value = shelter.id;
        option.textContent = shelter.name;
        option.selected = Number(selectedId) === Number(shelter.id);
        select.appendChild(option);
    });
    return select;
}

function actionButton(action, id, label, resource) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-primary btn-small";
    button.dataset.dashboardAction = action;
    button.dataset.recordId = id;
    if (resource) {
        button.dataset.resource = resource;
    }
    button.textContent = label;
    return button;
}

async function saveDashboardAction(button, role, message) {
    const row = button.closest("tr");
    const id = encodeURIComponent(button.dataset.recordId);
    const action = button.dataset.dashboardAction;
    let endpoint;
    let data;
    if (action === "save-status") {
        const resource = button.dataset.resource;
        endpoint = "api/dashboard/" + resource + "/" + id + "/status";
        data = { status: row.querySelector("[data-status-input]").value };
    } else {
        const resource = action === "assign-pet" ? "pets" : "rescues";
        endpoint = "api/dashboard/" + resource + "/" + id + "/assign";
        data = { shelterId: Number(row.querySelector("[data-shelter-input]").value) };
        if (!data.shelterId) {
            showDashboardMessage(message, "Select a shelter first.", true);
            return;
        }
    }
    button.disabled = true;
    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });
        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.error || "Unable to save this change.");
        }
        await loadDashboard(role, message);
        showDashboardMessage(message, result.message || "Saved.");
    } catch (error) {
        showDashboardMessage(message, error.message, true);
        button.disabled = false;
    }
}

async function submitPet(event, message) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = {
        name: document.getElementById("petName").value.trim(),
        species: document.getElementById("petSpecies").value,
        breed: document.getElementById("petBreed").value.trim(),
        age: Number(document.getElementById("petAge").value),
        gender: document.getElementById("petGender").value,
        location: document.getElementById("petLocation").value.trim(),
        health: document.getElementById("petHealth").value.trim(),
        image: document.getElementById("petImage").value.trim(),
        vaccinated: document.getElementById("petVaccinated").checked,
        dewormed: document.getElementById("petDewormed").checked,
        description: document.getElementById("petDescription").value.trim()
    };
    try {
        const response = await fetch("api/shelter/pets", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });
        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.error || "Unable to add pet.");
        }
        form.reset();
        await loadDashboard("shelter", message);
        showDashboardMessage(message, "Pet added.");
    } catch (error) {
        showDashboardMessage(message, error.message, true);
    }
}

function showDashboardMessage(element, text, isError) {
    if (!element) {
        return;
    }
    element.textContent = text;
    element.classList.toggle("error", Boolean(isError));
}

function formatDate(value) {
    return value ? new Date(value).toLocaleDateString() : "";
}