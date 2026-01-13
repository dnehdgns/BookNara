// ================= 내정보 수정/저장 =================
const editBtn = document.getElementById("editBtn");
const saveBtn = document.getElementById("saveBtn");
const editableInputs = document.querySelectorAll(".editable");
const addressBtn = document.querySelector(".address-row button");

let editMode = false;

if (editBtn) {
    editBtn.addEventListener("click", () => {
        editMode = !editMode;

        editableInputs.forEach(input => {
            input.disabled = !editMode;
            input.style.background = editMode ? "#fff" : "#f3f3f3";
        });

        if (addressBtn) addressBtn.disabled = !editMode;
        if (saveBtn) saveBtn.style.display = editMode ? "inline-block" : "none";

        editBtn.innerText = editMode ? "취소" : "수정";
    });
}

// ================= (선택) 다음 주소 API =================
function execDaumPostcode() {
    if (!window.daum || !daum.Postcode) {
        alert("다음 주소 API 스크립트가 로드되지 않았어요. myinfo.html에서 스크립트 주석을 해제해 주세요.");
        return;
    }

    new daum.Postcode({
        oncomplete: function (data) {
            const addressInput = document.querySelector("input[name='address']");
            if (addressInput) addressInput.value = data.address;
        }
    }).open();
} // ✅ 여기 닫는 중괄호가 빠져서 오류였음

// ================= 회원탈퇴 모달 =================
const withdrawBtn = document.getElementById("withdrawBtn");
const withdrawModal = document.getElementById("withdrawModal");
const withdrawCloseBtn = document.getElementById("withdrawCloseBtn");
const withdrawCancelBtn = document.getElementById("withdrawCancelBtn");
const withdrawConfirmBtn = document.getElementById("withdrawConfirmBtn");

const captchaImg = document.getElementById("captchaImg");
const captchaRefreshBtn = document.getElementById("captchaRefreshBtn");
const captchaInput = document.getElementById("captchaInput");
const withdrawPassword = document.getElementById("withdrawPassword");

function openWithdrawModal() {
    if (!withdrawModal) return;
    withdrawModal.style.display = "flex";
    refreshCaptcha();
    if (captchaInput) captchaInput.value = "";
    if (withdrawPassword) withdrawPassword.value = "";
}

function closeWithdrawModal() {
    if (!withdrawModal) return;
    withdrawModal.style.display = "none";
}

function refreshCaptcha() {
    if (!captchaImg) return;
    captchaImg.src = "/mypage/withdraw/captcha?ts=" + Date.now();
}

if (withdrawBtn) withdrawBtn.addEventListener("click", openWithdrawModal);
if (withdrawCloseBtn) withdrawCloseBtn.addEventListener("click", closeWithdrawModal);
if (withdrawCancelBtn) withdrawCancelBtn.addEventListener("click", closeWithdrawModal);
if (captchaRefreshBtn) captchaRefreshBtn.addEventListener("click", refreshCaptcha);

// 오버레이 클릭하면 닫기(모달 내부 클릭은 제외)
if (withdrawModal) {
    withdrawModal.addEventListener("click", (e) => {
        if (e.target === withdrawModal) closeWithdrawModal();
    });
}

if (withdrawConfirmBtn) {
    withdrawConfirmBtn.addEventListener("click", async () => {
        const captcha = (captchaInput?.value || "").trim();
        const password = (withdrawPassword?.value || "").trim();

        if (!captcha) {
            alert("보안문자를 입력해 주세요.");
            captchaInput?.focus();
            return;
        }
        if (!password) {
            alert("비밀번호를 입력해 주세요.");
            withdrawPassword?.focus();
            return;
        }

        try {
            withdrawConfirmBtn.disabled = true;

            const formData = new URLSearchParams();
            formData.append("captcha", captcha);
            formData.append("password", password);

            const res = await fetch("/mypage/withdraw", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
                body: formData.toString()
            });

            if (res.ok) {
                alert("회원 탈퇴 처리되었습니다.");
                window.location.href = "/";
                return;
            }

            const msg = await res.text();
            alert(msg || "탈퇴 처리에 실패했습니다.");
            refreshCaptcha();
        } catch (e) {
            alert("네트워크 오류가 발생했습니다.");
            refreshCaptcha();
        } finally {
            withdrawConfirmBtn.disabled = false;
        }
    });
}

//에러메시지
function showMsg(el, msg, success = false) {
  if (!el) return;
  el.textContent = msg;
  el.classList.toggle("success", success);
}
// ==== 비밀번호 재설정 ====
const pwModal = document.getElementById("pwModal");
const pwChangeBtn = document.getElementById("pwChangeBtn");
const pwCancelBtn = document.getElementById("pwCancelBtn");

if (pwModal && pwChangeBtn && pwCancelBtn) {

  pwChangeBtn.addEventListener("click", () => {
    resetPwModal();
    pwModal.style.display = "flex";
  });

  pwCancelBtn.addEventListener("click", () => {
    pwModal.style.display = "none";
  });


}

function resetPwModal() {
  const currentPw = document.getElementById("currentPw");
  const newPw = document.getElementById("newPw");
  const newPwConfirm = document.getElementById("newPwConfirm");
  const pwSubmitBtn = document.getElementById("pwSubmitBtn");
  const currentPwMsg = document.getElementById("currentPwMsg");
    const newPwMsg = document.getElementById("newPwMsg");
    const newPwConfirmMsg = document.getElementById("newPwConfirmMsg");

  if (currentPw) currentPw.value = "";
  if (newPw) {
    newPw.value = "";
    newPw.disabled = true;
  }
  if (newPwConfirm) {
    newPwConfirm.value = "";
    newPwConfirm.disabled = true;
  }
  if (pwSubmitBtn) pwSubmitBtn.disabled = true;

  showMsg(currentPwMsg, "");
    showMsg(newPwMsg, "");
    showMsg(newPwConfirmMsg, "");
}


// ==== 현재 비밀번호 검증 ====
const verifyPwBtn = document.getElementById("verifyPwBtn");
const currentPwInput = document.getElementById("currentPw");
const newPwInput = document.getElementById("newPw");
const newPwConfirmInput = document.getElementById("newPwConfirm");
const currentPwMsg = document.getElementById("currentPwMsg");

verifyPwBtn?.addEventListener("click", async () => {
  const currentPw = currentPwInput.value.trim();
  showMsg(currentPwMsg, "");

  if (!currentPw) {
    showMsg(currentPwMsg, "현재 비밀번호를 입력해 주세요.");
    currentPwInput.focus();
    return;
  }

  try {
    const res = await fetch("/mypage/password/verify", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ password: currentPw })
    });

    if (!res.ok) {
      const msg = await res.text();
      showMsg(currentPwMsg, msg || "비밀번호가 일치하지 않습니다.");
      return;
    }

    // ✅ 성공
    showMsg(currentPwMsg, "✔ 확인되었습니다.", true);
    newPwInput.disabled = false;
    newPwConfirmInput.disabled = false;
    newPwInput.focus();

  } catch (e) {
     showMsg(currentPwMsg, "네트워크 오류가 발생했습니다.");
  }
});

const pwSubmitBtn = document.getElementById("pwSubmitBtn");
const newPwMsg = document.getElementById("newPwMsg");
const newPwConfirmMsg = document.getElementById("newPwConfirmMsg");

// 비밀번호 규칙 (서비스랑 동일)
const pwRegex =
  /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,16}$/;

function validateNewPassword() {
  const currentPw = currentPwInput.value;
  const newPw = newPwInput.value;
  const confirmPw = newPwConfirmInput.value;

  showMsg(newPwMsg, "");
    showMsg(newPwConfirmMsg, "");

  // 정규식
  if (!pwRegex.test(newPw)) {
  showMsg(newPwMsg, "영문, 숫자, 특수문자 포함 8~16자로 입력해 주세요.");
    pwSubmitBtn.disabled = true;
    return;
  }

  // 새 비밀번호 확인
  if (newPw !== confirmPw) {
    showMsg(newPwConfirmMsg, "비밀번호가 일치하지 않습니다.");
    pwSubmitBtn.disabled = true;
    return;
  }

  // 기존 비밀번호와 동일 방지
  if (newPw === currentPw) {
    showMsg(newPwMsg, "기존 비밀번호와 동일합니다.");
    pwSubmitBtn.disabled = true;
    return;
  }

   if (confirmPw) {
      showMsg(newPwConfirmMsg, "✔ 비밀번호가 일치합니다.", true);
    }

  pwSubmitBtn.disabled = false;
}

newPwInput?.addEventListener("input", validateNewPassword);
newPwConfirmInput?.addEventListener("input", validateNewPassword);

// ==== 비밀번호 변경 ====
pwSubmitBtn?.addEventListener("click", async () => {
  pwSubmitBtn.disabled = true;

  try {
    const res = await fetch("/mypage/password/change", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ newPassword: newPwInput.value.trim() })
    });

    if (!res.ok) {
      const msg = await res.text();
      showMsg(newPwConfirmMsg, msg || "비밀번호 변경 실패");
      pwSubmitBtn.disabled = false;
      return;
    }

    alert("비밀번호가 변경되었습니다."); // ✅ 완료 알림만 유지
    pwModal.style.display = "none";

  } catch (e) {
    showMsg(newPwConfirmMsg, "네트워크 오류가 발생했습니다.");
    pwSubmitBtn.disabled = false;
  }


});
