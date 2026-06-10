import React, { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Eye, EyeOff } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldError,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import api, { isAxiosError } from "../../lib/axios";
import { useAuthStore } from "../../store/authStore";

const Login = ({ className, ...props }: React.ComponentProps<"div">) => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({});
  const [error, setError] = useState("");
  const setAuth = useAuthStore((state) => state.setAuth);
  const navigate = useNavigate();

  const validate = () => {
    const newErrors: { username?: string; password?: string } = {};
    if (!username) newErrors.username = "Username is required";
    if (!password) newErrors.password = "Password is required";
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!validate()) return;

    setIsLoading(true);

    try {
      const response = await api.post("/authenticate", { username, password });
      const { token } = response.data;

      setAuth(token, null);

      const userResponse = await api.get("/users/me");
      setAuth(token, userResponse.data);

      navigate("/");
    } catch (err) {
      let message = "Failed to login. Please check your credentials.";
      if (isAxiosError(err) && err.response?.data) {
        const data = err.response.data as { message?: string };
        if (data.message) {
          message = data.message;
        }
      }
      setError(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className={cn("flex flex-col gap-6 items-center justify-center min-h-[calc(100vh-80px)]", className)} {...props}>
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-center text-2xl">Login to Splitz</CardTitle>
          <CardDescription className="text-center">
            Enter your credentials below to login to your account
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} noValidate>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="username">Username</FieldLabel>
                <Input
                  id="username"
                  type="text"
                  placeholder="yourusername"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (errors.username) {
                      setErrors((prev) => ({ ...prev, username: undefined }));
                    }
                  }}
                  required
                />
                {errors.username && <FieldError>{errors.username}</FieldError>}
              </Field>
              <Field>
                <div className="flex items-center">
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                </div>
                <div className="relative">
                  <Input 
                    id="password" 
                    type={showPassword ? "text" : "password"} 
                    placeholder="••••••••"
                    value={password}
                    onChange={(e) => {
                      setPassword(e.target.value);
                      if (errors.password) {
                        setErrors((prev) => ({ ...prev, password: undefined }));
                      }
                    }}
                    required 
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label="toggle visibility"
                  >
                    {showPassword ? (
                      <EyeOff size={16} data-lucide="eye-off" />
                    ) : (
                      <Eye size={16} data-lucide="eye" />
                    )}
                  </button>
                </div>
                {errors.password && <FieldError>{errors.password}</FieldError>}
              </Field>
              {error && (
                <p className="text-sm font-medium text-red-500 text-center">{error}</p>
              )}
              <Field>
                <Button type="submit" className="w-full" disabled={isLoading}>
                  {isLoading ? "Logging in..." : "Login"}
                </Button>
                <div className="text-center text-sm text-muted-foreground mt-4">
                  Don&apos;t have an account?{" "}
                  <Link to="/register" className="underline underline-offset-4 hover:text-primary">
                    Register here
                  </Link>
                </div>
              </Field>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  );
};

export default Login;
