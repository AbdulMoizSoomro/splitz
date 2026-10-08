import { Link } from "react-router-dom";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Users } from "lucide-react";
import type { Group } from "../../../types/group";

interface FriendMutualGroupsTabProps {
  sharedGroups: Group[];
}

export function FriendMutualGroupsTab({
  sharedGroups,
}: FriendMutualGroupsTabProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Shared Groups</CardTitle>
      </CardHeader>
      <CardContent>
        {sharedGroups.length > 0 ? (
          <div className="space-y-3">
            {sharedGroups.map((group) => (
              <Card
                key={group.id}
                className="hover:bg-muted/50 transition-colors border-border shadow-sm"
              >
                <Link to={`/groups/${group.id}`}>
                  <CardContent className="flex items-center justify-between p-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-orange-100 dark:bg-orange-900/30 flex items-center justify-center text-orange-600 dark:text-orange-400">
                        <Users size={20} />
                      </div>
                      <span className="font-medium text-foreground">
                        {group.name}
                      </span>
                    </div>
                  </CardContent>
                </Link>
              </Card>
            ))}
          </div>
        ) : (
          <p className="text-muted-foreground italic">
            No shared groups found.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
