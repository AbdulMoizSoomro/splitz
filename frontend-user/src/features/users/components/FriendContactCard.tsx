import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Mail } from "lucide-react";
import type { User } from "../../../types/user";

interface FriendContactCardProps {
  friend: User;
}

export function FriendContactCard({ friend }: FriendContactCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Contact Information</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center gap-2 text-muted-foreground">
          <Mail size={18} />
          <span>{friend.email}</span>
        </div>
      </CardContent>
    </Card>
  );
}
