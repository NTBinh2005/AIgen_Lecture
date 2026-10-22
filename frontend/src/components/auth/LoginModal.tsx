import { useEffect, useState, forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff, Atom, FileText, BarChart2, Sparkles } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useNavigate, useSearchParams } from "react-router-dom";
import { homeAfterLogin } from "@/lib/roles";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Dialog, DialogContent, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import axiosInstance from "@/api/axiosInstance";
import { notify } from "@/lib/notify";
import { getErrorStatus } from "@/lib/format";

// ─── Schemas ─────────────────────────────────────────────────────────────────

const loginSchema = z.object({
  email: z.string().email("Email không hợp lệ"),
  password: z.string().min(8, "Mật khẩu tối thiểu 8 ký tự"),
});

/** Thông tin đăng ký giữ lại để gửi lại mã OTP mà không phải nhập lại form. */
type RegisterPayload = { name: string; email: string; password: string; role: "STUDENT" | "TEACHER" };
type PendingRegistrationFlow = {
  payload: RegisterPayload;
  otp: string;
  expiresInMinutes: number;
  resendAt: number;
};
const RESEND_COOLDOWN_MS = 60_000;
const currentTime = () => Date.now();
const ROLE_OPTIONS = [["STUDENT", "Học viên"], ["TEACHER", "Giáo viên"]] as const;

const registerSchema = z.object({
  name: z.string().min(2, "Họ tên tối thiểu 2 ký tự"),
  email: z.string().email("Email không hợp lệ"),
  password: z.string().min(8, "Mật khẩu tối thiểu 8 ký tự"),
  confirmPassword: z.string().min(1, "Vui lòng nhập lại mật khẩu"),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Mật khẩu xác nhận không khớp",
  path: ["confirmPassword"],
});

const forgotSchema = z.object({
  email: z.string().email("Email không hợp lệ"),
});

type LoginForm = z.infer<typeof loginSchema>;
type RegisterForm = z.infer<typeof registerSchema>;
type ForgotForm = z.infer<typeof forgotSchema>;
type Tab = "login" | "register" | "forgot";

// ─── Feature list (left panel) ───────────────────────────────────────────────

const features = [
  {
    icon: <FileText size={16} aria-hidden="true" />,
    title: "Trích xuất thông minh",
    text: "Tự động tạo slide & quiz từ PDF, DOCX chỉ trong vài giây.",
  },
  {
    icon: <BarChart2 size={16} aria-hidden="true" />,
    title: "Phân tích Real-time",
    text: "Theo dõi kết quả học tập và hiểu rõ tiến độ của học sinh.",
  },
  {
    icon: <Sparkles size={16} aria-hidden="true" />,
    title: "Gợi ý cải tiến bằng AI",
    text: "Nâng cao chất lượng bài giảng nhờ dữ liệu phân tích chuyên sâu.",
  },
];

// ─── Shared Styles ────────────────────────────────────────────────────────────

const inputClass = "h-11 bg-muted/20 border-border/60 focus-visible:ring-primary/30 transition-all rounded-xl";
const submitBtnClass = "w-full h-11 mt-2 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 shadow-lg shadow-primary/25 hover:-translate-y-0.5 transition-all duration-300 font-semibold";

// ─── Field wrapper ────────────────────────────────────────────────────────────

function Field({
  label,
  error,
  htmlFor,
  children,
}: {
  label: string;
  error?: string;
  htmlFor?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5 group">
      <label
        htmlFor={htmlFor}
        className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground group-focus-within:text-primary transition-colors"
      >
        {label}
      </label>
      {children}
      {error && <p className="text-xs font-medium text-destructive mt-0.5">{error}</p>}
    </div>
  );
}

// ─── Password Input ───────────────────────────────────────────────────────────

const PasswordInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ placeholder, ...props }, ref) => {
    const [show, setShow] = useState(false);
    return (
      <div className="relative">
        <Input
          ref={ref}
          type={show ? "text" : "password"}
          placeholder={placeholder}
          className={inputClass}
          {...props}
        />
        <button
          type="button"
          onClick={() => setShow(!show)}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors p-1 rounded-md hover:bg-muted"
          aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        >
          {show ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
    );
  }
);
PasswordInput.displayName = "PasswordInput";

// ─── Forms ────────────────────────────────────────────────────────────────────

function LoginForm({ onForgot }: { onForgot: () => void }) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  const { setAuth } = useAuthStore();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const onSubmit = async (data: LoginForm) => {
    try {
      const response = await axiosInstance.post("/auth/login", data);
      const { token, refreshToken, userId, name, email, role } = response.data;
      setAuth({ id: userId.toString(), name, email, role }, token, refreshToken ?? null);
      
      notify.success(`Chào mừng ${name}!`);
      // Mở link cần đăng nhập (VD quét mã QR điểm danh) → đăng nhập xong quay lại đúng trang đó
      navigate(homeAfterLogin(role, searchParams.get("next")));
    } catch (error: unknown) {
      console.error("Login error:", error);
      notify.apiError(error, "Đăng nhập thất bại");
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5 animate-in fade-in zoom-in-95 duration-500">
      <div className="mb-2">
        <h2 className="text-3xl font-bold text-foreground tracking-tight">
          Chào mừng trở lại
        </h2>
        <p className="text-sm text-muted-foreground mt-2">
          Đăng nhập để tiếp tục khám phá sức mạnh của AI trong giáo dục.
        </p>
      </div>

      <Field label="Email" error={errors.email?.message} htmlFor="login-email">
        <Input
          id="login-email"
          type="email"
          placeholder="you@example.com"
          className={inputClass}
          {...register("email")}
        />
      </Field>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between">
          <label htmlFor="login-password" className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground focus-within:text-primary transition-colors">
            Mật khẩu
          </label>
          <button
            type="button"
            onClick={onForgot}
            className="text-[11px] font-semibold text-primary hover:text-primary/80 transition-colors"
          >
            Quên mật khẩu?
          </button>
        </div>
        <PasswordInput id="login-password" placeholder="••••••••" {...register("password")} />
        {errors.password?.message && (
          <p className="text-xs font-medium text-destructive mt-0.5">{errors.password.message}</p>
        )}
      </div>

      <Button
        type="submit"
        disabled={isSubmitting}
        className={submitBtnClass}
      >
        {isSubmitting ? "Đang đăng nhập..." : "Đăng nhập vào hệ thống"}
      </Button>
      {/* Đăng nhập Google: backend có /auth/google nhưng FE chưa tích hợp Google Identity Services → tạm ẩn */}
    </form>
  );
}

function RegisterForm({
  flow,
  setFlow,
}: {
  flow: PendingRegistrationFlow | null;
  setFlow: (flow: PendingRegistrationFlow | null | ((current: PendingRegistrationFlow | null) => PendingRegistrationFlow | null)) => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
  });

  const { setAuth } = useAuthStore();
  const navigate = useNavigate();
  const [role, setRole] = useState<"STUDENT" | "TEACHER">("STUDENT");
  const pending = flow?.payload ?? null;
  const pendingEmail = pending?.email ?? null;
  const otp = flow?.otp ?? "";
  const [verifying, setVerifying] = useState(false);
  const [resending, setResending] = useState(false);
  // Backend chặn gửi lại mã trong 60 giây → đếm ngược trên nút "Gửi lại mã"
  const [now, setNow] = useState(currentTime);
  useEffect(() => {
    if (!pendingEmail) return;
    const id = setInterval(() => setNow(currentTime()), 1000);
    return () => clearInterval(id);
  }, [pendingEmail]);
  const resendIn = Math.max(0, Math.ceil(((flow?.resendAt ?? 0) - now) / 1000));

  /** Bước 1: backend lưu đăng ký tạm và gửi OTP tới email (chưa tạo tài khoản). */
  const sendOtp = async (payload: RegisterPayload) => {
    const res = await axiosInstance.post<{ message: string; expiresInSeconds: number }>("/auth/register", payload);
    const sentAt = currentTime();
    setFlow({
      payload,
      otp: "",
      expiresInMinutes: Math.max(1, Math.round((res.data?.expiresInSeconds ?? 300) / 60)),
      resendAt: sentAt + RESEND_COOLDOWN_MS,
    });
    setNow(sentAt);
    notify.success("Đã gửi mã OTP", `Vui lòng kiểm tra hộp thư (cả mục Spam) của ${payload.email}`);
  };

  const onSubmit = async (data: RegisterForm) => {
    try {
      await sendOtp({ name: data.name.trim(), email: data.email.trim(), password: data.password, role });
    } catch (error: unknown) {
      notify.apiError(error, "Đăng ký thất bại");
    }
  };

  const onResend = async () => {
    if (!pending) return;
    setResending(true);
    try {
      await sendOtp(pending);
    } catch (error: unknown) {
      notify.apiError(error, "Chưa gửi lại được mã");
    } finally {
      setResending(false);
    }
  };

  const onVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pendingEmail) return;
    setVerifying(true);
    try {
      const response = await axiosInstance.post("/auth/register/verify", { email: pendingEmail, otp: otp.trim() });
      const { token, refreshToken, userId, name, email, role } = response.data;
      setFlow(null);
      setAuth({ id: userId.toString(), name, email, role }, token, refreshToken ?? null);
      notify.success("Đăng ký thành công", `Chào mừng ${name}!`);
      navigate(role === "TEACHER" ? "/teacher" : role === "ADMIN" ? "/admin" : "/student");
    } catch (error: unknown) {
      // Backend trả 401 với câu chung "Thông tin đăng nhập không chính xác" cho mọi lỗi OTP (FIX.md #83)
      if (getErrorStatus(error) === 401) notify.error("Mã OTP không đúng hoặc đã hết hạn", "Nhập sai quá 5 lần thì mã bị khoá — bấm \"Gửi lại mã\" để nhận mã mới.");
      else notify.apiError(error, "Xác nhận OTP thất bại");
      setFlow((current) => current ? { ...current, otp: "" } : null);
    } finally {
      setVerifying(false);
    }
  };

  if (pendingEmail) {
    return (
      <form onSubmit={onVerify} className="flex flex-col gap-5 animate-in fade-in zoom-in-95 duration-500">
        <div className="mb-2">
          <h2 className="text-3xl font-bold text-foreground tracking-tight">Xác nhận email</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Nhập mã OTP 6 số đã gửi tới <strong className="text-foreground">{pendingEmail}</strong>. Mã có hiệu lực trong {flow?.expiresInMinutes ?? 5} phút.
          </p>
        </div>
        <Field label="Mã OTP" htmlFor="register-otp">
          <Input
            id="register-otp"
            autoFocus
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="123456"
            className={cn(inputClass, "text-center text-lg tracking-[0.5em] font-semibold")}
            value={otp}
            onChange={(ev) => setFlow((current) => current
              ? { ...current, otp: ev.target.value.replace(/\D/g, "") }
              : null)}
          />
        </Field>
        <Button type="submit" disabled={verifying || otp.length !== 6} className={submitBtnClass}>
          {verifying ? "Đang xác nhận..." : "Xác nhận & hoàn tất đăng ký"}
        </Button>
        <div className="flex items-center justify-between text-xs">
          <button
            type="button"
            onClick={() => setFlow(null)}
            className="font-semibold text-muted-foreground hover:text-foreground transition-colors"
          >
            ← Sửa thông tin
          </button>
          <button
            type="button"
            onClick={onResend}
            disabled={resending || resendIn > 0}
            className="font-semibold text-primary hover:text-primary/80 transition-colors disabled:text-muted-foreground disabled:cursor-not-allowed"
          >
            {resending ? "Đang gửi..." : resendIn > 0 ? `Gửi lại mã sau ${resendIn} giây` : "Gửi lại mã"}
          </button>
        </div>
      </form>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5 animate-in fade-in zoom-in-95 duration-500">
      <div className="mb-2">
        <h2 className="text-3xl font-bold text-foreground tracking-tight">
          Tạo tài khoản mới
        </h2>
        <p className="text-sm text-muted-foreground mt-2">
          Tham gia EduMind ngay hôm nay để tạo bài giảng tương tác.
        </p>
      </div>

      <div className="space-y-1.5">
        <span className="text-sm font-medium text-foreground">Bạn là</span>
        <div role="radiogroup" aria-label="Vai trò" className="grid grid-cols-2 gap-2 rounded-xl bg-muted/40 p-1">
          {ROLE_OPTIONS.map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={role === value}
              onClick={() => setRole(value)}
              className={cn(
                "h-10 rounded-lg text-sm font-medium transition-colors",
                role === value ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <Field label="Họ và tên" error={errors.name?.message} htmlFor="register-name">
        <Input 
          id="register-name"
          type="text" 
          placeholder="Nguyễn Văn A" 
          className={inputClass}
          {...register("name")} 
        />
      </Field>

      <Field label="Email" error={errors.email?.message} htmlFor="register-email">
        <Input
          id="register-email"
          type="email"
          placeholder="you@example.com"
          className={inputClass}
          {...register("email")}
        />
      </Field>

      <Field label="Mật khẩu" error={errors.password?.message} htmlFor="register-password">
        <PasswordInput
          id="register-password"
          placeholder="Tối thiểu 8 ký tự"
          {...register("password")}
        />
      </Field>

      <Field label="Xác nhận mật khẩu" error={errors.confirmPassword?.message} htmlFor="register-confirm-password">
        <PasswordInput
          id="register-confirm-password"
          placeholder="Nhập lại mật khẩu"
          {...register("confirmPassword")}
        />
      </Field>

      <Button
        type="submit"
        disabled={isSubmitting}
        className={submitBtnClass}
      >
        {isSubmitting ? "Đang đăng ký..." : "Tạo tài khoản miễn phí"}
      </Button>
    </form>
  );
}

function ForgotForm({ onBack, isActive }: { onBack: () => void; isActive?: boolean }) {
  const [sent, setSent] = useState(false);
  
  // Reset trạng thái khi tab không còn active (chỉnh state ngay lúc render, không cần effect)
  if (!isActive && sent) setSent(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotForm>({
    resolver: zodResolver(forgotSchema),
  });

  const onSubmit = async () => {
    // TODO: Call forgot password API
    setSent(true);
  };

  if (sent) {
    return (
      <div className="flex flex-col gap-6 animate-in fade-in zoom-in-95 duration-500">
        <div className="w-14 h-14 rounded-2xl bg-primary/10 flex items-center justify-center border border-primary/20 shadow-inner">
          <Sparkles size={24} className="text-primary" />
        </div>
        <div>
          <h2 className="text-3xl font-bold text-foreground tracking-tight">
            Kiểm tra email
          </h2>
          <p className="text-sm text-muted-foreground mt-3 leading-relaxed">
            Chúng tôi đã gửi link đặt lại mật khẩu đến email của bạn. Vui lòng kiểm tra cả hộp thư rác (spam).
          </p>
        </div>
        <button
          type="button"
          onClick={onBack}
          className="text-sm font-semibold text-primary hover:text-primary/80 transition-colors self-start flex items-center gap-1 mt-2"
        >
          ← Quay lại đăng nhập
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5 animate-in fade-in zoom-in-95 duration-500">
      <div className="mb-2">
        <h2 className="text-3xl font-bold text-foreground tracking-tight">
          Quên mật khẩu?
        </h2>
        <p className="text-sm text-muted-foreground mt-2 leading-relaxed">
          Đừng lo, chỉ cần nhập email của bạn và chúng tôi sẽ gửi link đặt lại mật khẩu ngay.
        </p>
      </div>

      <Field label="Email" error={errors.email?.message} htmlFor="forgot-email">
        <Input
          id="forgot-email"
          type="email"
          placeholder="you@example.com"
          className={inputClass}
          {...register("email")}
        />
      </Field>

      <Button
        type="submit"
        disabled={isSubmitting}
        className={submitBtnClass}
      >
        {isSubmitting ? "Đang gửi..." : "Gửi link đặt lại"}
      </Button>

      <button
        type="button"
        onClick={onBack}
        className="text-sm font-semibold text-muted-foreground hover:text-foreground transition-colors self-start flex items-center gap-1 mt-2"
      >
        ← Quay lại đăng nhập
      </button>
    </form>
  );
}

// ─── Main Modal Component ─────────────────────────────────────────────────────

interface LoginModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function LoginModal({ isOpen, onOpenChange }: LoginModalProps) {
  const [tab, setTab] = useState<Tab>("login");
  const [registrationFlow, setRegistrationFlow] = useState<PendingRegistrationFlow | null>(null);
  const tabs: { id: Tab; label: string }[] = [
    { id: "login", label: "Đăng nhập" },
    { id: "register", label: "Đăng ký" },
  ];

  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
      <DialogContent className="p-0 border-none bg-transparent shadow-none max-w-[95vw] sm:max-w-5xl overflow-hidden rounded-[2rem] sm:rounded-[2rem]">
        <DialogTitle className="sr-only">Đăng nhập EduMind</DialogTitle>
        <DialogDescription className="sr-only">Form đăng nhập hoặc đăng ký tài khoản mới</DialogDescription>
        
        <div className="w-full flex shadow-2xl min-h-[600px] max-h-[90vh] overflow-y-auto border border-border/50">
          {/* ── Left panel (Glassmorphism) ── */}
          <div className="hidden lg:flex flex-col flex-1 relative overflow-hidden bg-background/20 backdrop-blur-2xl border-r border-border/50 p-12">
            {/* Animated background shapes */}
            <div className="absolute top-0 right-0 -mt-20 -mr-20 w-80 h-80 rounded-full bg-primary/10 blur-3xl animate-pulse [animation-duration:4s]" />
            <div className="absolute bottom-0 left-0 -mb-20 -ml-20 w-80 h-80 rounded-full bg-primary/10 blur-3xl animate-pulse [animation-duration:5s]" />
            
            <div className="relative z-10">
              <div className="inline-flex items-center gap-3 group">
                <div className="w-10 h-10 rounded-xl bg-background/50 backdrop-blur-md border border-border/50 flex items-center justify-center shadow-lg transition-transform group-hover:scale-105">
                  <Atom size={20} className="text-primary" aria-hidden="true" />
                </div>
                <span className="font-bold text-2xl text-foreground tracking-tight">
                  EduMind
                </span>
              </div>
            </div>

            <div className="relative z-10 flex flex-col gap-10 mt-16 mb-auto">
              <div>
                <h3 className="text-4xl font-bold text-foreground leading-tight mb-4">
                  Học tập <br />
                  <span className="text-transparent bg-clip-text bg-gradient-to-r from-primary to-violet-500">
                    không giới hạn
                  </span>
                </h3>
                <p className="text-muted-foreground text-base leading-relaxed max-w-sm">
                  EduMind sử dụng AI để biến tài liệu khô khan thành bài giảng tương tác hấp dẫn chỉ trong vài giây.
                </p>
              </div>

              <div className="flex flex-col gap-4">
                {features.map((f, i) => (
                  <div key={i} className="flex items-start gap-4 p-4 rounded-2xl bg-background/40 border border-border/50 backdrop-blur-md hover:bg-background/60 transition-colors shadow-sm group/card">
                    <div className="w-10 h-10 shrink-0 rounded-xl bg-background/50 border border-border/30 flex items-center justify-center text-primary shadow-inner transition-transform group-hover/card:scale-110">
                      {f.icon}
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-foreground">{f.title}</h4>
                      <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                        {f.text}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* ── Right panel ── */}
          <div className="flex-1 bg-background p-8 sm:p-12 md:p-16 flex flex-col relative">
            <div className="flex-1 flex flex-col justify-center max-w-sm mx-auto w-full">
              {/* Tabs */}
              {tab !== "forgot" && (
                <div role="tablist" className="flex gap-2 mb-10 p-1 bg-muted/30 rounded-xl w-fit border border-border/50">
                  {tabs.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      role="tab"
                      aria-selected={tab === t.id}
                      onClick={() => setTab(t.id)}
                      className={cn(
                        "px-6 py-2 text-sm font-bold transition-all rounded-lg",
                        tab === t.id
                          ? "bg-background text-foreground shadow-sm"
                          : "text-muted-foreground hover:text-foreground hover:bg-muted/50",
                      )}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>
              )}

              {/* Form content */}
              <div className="relative grid">
                <div className={cn("col-start-1 row-start-1 transition-all duration-500", tab === "login" ? "opacity-100 z-10 translate-x-0" : "opacity-0 pointer-events-none z-0 -translate-x-4")}>
                  <LoginForm onForgot={() => setTab("forgot")} />
                </div>
                <div className={cn("col-start-1 row-start-1 transition-all duration-500", tab === "register" ? "opacity-100 z-10 translate-x-0" : "opacity-0 pointer-events-none z-0 translate-x-4")}>
                  <RegisterForm flow={registrationFlow} setFlow={setRegistrationFlow} />
                </div>
                <div className={cn("col-start-1 row-start-1 transition-all duration-500", tab === "forgot" ? "opacity-100 z-10 translate-y-0" : "opacity-0 pointer-events-none z-0 translate-y-4")}>
                  <ForgotForm onBack={() => setTab("login")} isActive={tab === "forgot"} />
                </div>
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
