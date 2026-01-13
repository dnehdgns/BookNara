document.addEventListener("DOMContentLoaded", () => {

    /* ================= 공통 DOM ================= */
    const editBtn = document.getElementById("editBtn");
    const saveBtn = document.getElementById("saveBtn");
    const form = document.querySelector(".info-form");
    const editableInputs = document.querySelectorAll(".editable");
    const addressBtn = document.getElementById("addressBtn");
    const genreCards = document.querySelectorAll(".genre-card");

    /* ================= 닉네임 DOM ================= */
    const profileInput = document.getElementById("profileNm");
    const originalProfileInput = document.getElementById("originalProfileNm");
    const msg = document.getElementById("profileNmMsg");

    let editMode = false;
    let selectedGenres = [];
    let profileNmValid = true; // ✅ 닉네임 중복 체크 상태

    /* ================= 방어 코드 ================= */
    if (!form || !editBtn) return;

    /* ================= 초기 장르 상태 수집 ================= */
    genreCards.forEach(card => {
        if (card.classList.contains("active")) {
            selectedGenres.push(Number(card.dataset.genreId));
        }
    });

    /* ================= 수정 버튼 ================= */
    editBtn.addEventListener("click", () => {
        editMode = !editMode;

        editableInputs.forEach(input => {
            input.disabled = !editMode;
            input.style.background = editMode ? "#fff" : "#f3f3f3";
        });

        if (addressBtn) addressBtn.disabled = !editMode;
        if (saveBtn) saveBtn.style.display = editMode ? "inline-block" : "none";

        genreCards.forEach(card => {
            card.classList.toggle("disabled", !editMode);
            card.dataset.editable = editMode ? "true" : "false";
        });

        // 🔹 수정 모드 진입 시 닉네임 상태 초기화
        if (editMode && msg) {
            msg.textContent = "";
            profileNmValid = true;
        }

        editBtn.innerText = editMode ? "취소" : "수정";
    });

    /* ================= 닉네임 중복 체크 ================= */
    if (profileInput && originalProfileInput && msg) {

        profileInput.addEventListener("blur", () => {
            if (!editMode) return;

            const profileNm = profileInput.value.trim();
            const originalProfileNm = originalProfileInput.value;

            if (!profileNm) return;

            // ✅ 기존 닉네임이면 통과
            if (profileNm === originalProfileNm) {
                msg.textContent = "현재 사용 중인 닉네임입니다";
                msg.className = "input-msg ok";
                profileNmValid = true;
                return;
            }

            fetch("/mypage/profile/check", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded"
                },
                body: new URLSearchParams({
                    profileNm,
                    originalProfileNm
                })
            })
                .then(res => res.json())
                .then(isAvailable => {
                    profileNmValid = isAvailable;
                    msg.textContent = isAvailable
                        ? "사용 가능한 닉네임입니다"
                        : "이미 사용 중인 닉네임입니다";
                    msg.className = "input-msg " + (isAvailable ? "ok" : "fail");
                })
                .catch(() => {
                    profileNmValid = false;
                    msg.textContent = "닉네임 확인 중 오류 발생";
                    msg.className = "input-msg fail";
                });
        });
    }

    /* ================= 장르 클릭 ================= */
    genreCards.forEach(card => {
        card.addEventListener("click", () => {
            if (card.dataset.editable !== "true") return;

            const id = Number(card.dataset.genreId);

            if (card.classList.contains("active")) {
                card.classList.remove("active");
                selectedGenres = selectedGenres.filter(v => v !== id);
            } else {
                if (selectedGenres.length >= 3) {
                    alert("장르는 최대 3개까지 선택할 수 있어요");
                    return;
                }
                card.classList.add("active");
                selectedGenres.push(id);
            }
        });
    });

    /* ================= 저장 버튼 ================= */
    saveBtn?.addEventListener("click", async (e) => {
        e.preventDefault();

        // 🔥 닉네임 중복 체크 실패 시 저장 차단
        if (!profileNmValid) {
            alert("닉네임 중복을 확인해주세요.");
            profileInput?.focus();
            return;
        }

        try {
            // 1️⃣ 장르 저장
            const res = await fetch("/mypage/myinfo/genres", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(selectedGenres)
            });
            if (!res.ok) throw new Error("genre save failed");

            // 2️⃣ disabled 해제 후 submit
            editableInputs.forEach(input => input.disabled = false);
            if (addressBtn) addressBtn.disabled = false;

            form.submit();

        } catch (err) {
            console.error(err);
            alert("저장 중 오류가 발생했습니다.");
        }
    });
});
