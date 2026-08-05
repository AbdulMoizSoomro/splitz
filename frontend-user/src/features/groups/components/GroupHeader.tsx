import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { Group } from "../../types/group";

interface GroupHeaderProps {
  group: Group;
}

export function GroupHeader({ group }: GroupHeaderProps) {
  const navigate = useNavigate();

  return (
    <div className="flex items-center gap-4 shrink-0">
      <Button variant="ghost" size="sm" onClick={() => navigate("/groups")}>
        <ArrowLeft size={20} />
      </Button>
      <div>
        <h1 className="text-2xl font-bold text-foreground">{group.name}</h1>
        {group.description && (
          <p className="text-muted-foreground">{group.description}</p>
        )}
      </div>
    </div>
  );
}
