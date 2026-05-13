import React, { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Eye, EyeOff } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "../../components/core/Card/Card";
import Input from "../../components/core/Input/Input";
import Button from "../../components/core/Button/Button";
import api, { isAxiosError } from "../../lib/axios";
import { useAuthStore } from "../../store/authStore";

const Login = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({});
  const [error, setError] = useState("");
  const setAuth = useAuthStore((state) => state.setAuth);
  const navigate = useNavigate();

  const togglePasswordVisibility = () => {
    setShowPassword(!showPassword);
  };

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
    <div className="flex items-center justify-center min-h-[calc(100vh-80px)]">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-center">Login to Splitz</CardTitle>
        </CardHeader>
        <form onSubmit={handleSubmit} noValidate>
          <CardContent className="space-y-4">
            <Input
              label="Username"
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                if (errors.username) {
                  setErrors((prev) => ({ ...prev, username: undefined }));
                }
              }}
              placeholder="yourusername"
              error={errors.username}
            />
            <Input
              label="Password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (errors.password) {
                  setErrors((prev) => ({ ...prev, password: undefined }));
                }
              }}
              placeholder="••••••••"
              error={errors.password}
              rightElement={
                <button
                  type="button"
                  onClick={togglePasswordVisibility}
                  className="text-gray-500 hover:text-gray-700 focus:outline-none"
                  aria-label="Toggle visibility"
                >
                  {showPassword ? (
                    <EyeOff size={20} data-lucide="eye-off" />
                  ) : (
                    <Eye size={20} data-lucide="eye" />
                  )}
                </button>
              }
            />
            {error && (
              <p className="text-sm font-medium text-red-500">{error}</p>
            )}
          </CardContent>
          <CardFooter className="flex flex-col gap-4">
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? "Logging in..." : "Login"}
            </Button>
            <p className="text-sm text-center text-gray-600">
              Don't have an account?{" "}
              <Link to="/register" className="text-blue-600 hover:underline">
                Register here
              </Link>
            </p>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
};

export default Login;
