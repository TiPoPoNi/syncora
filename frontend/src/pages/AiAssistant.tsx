import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { AiAssistantPanel } from '../components/AiAssistantPanel';
import { useOrganization } from '../context/OrganizationContext';
import { Bot, RefreshCw } from 'lucide-react';

interface Project {
  id: number;
  name: string;
}

export function AiAssistant() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const { currentOrg } = useOrganization();

  useEffect(() => {
    let isMounted = true;
    setLoading(true);
    api.get('/projects')
      .then((res) => {
        if (!isMounted) return;
        const list = res.data || [];
        setProjects(list);
        if (list.length > 0) {
          setSelectedProjectId(list[0].id);
        } else {
          setSelectedProjectId(null);
        }
      })
      .catch((err) => {
        if (!isMounted) return;
        console.error('Failed to load projects for AI Assistant:', err);
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [currentOrg?.id]);

  const selectedProject = projects.find((p) => p.id === selectedProjectId);

  return (
    <div className="space-y-6 animate-fadeIn pb-12">
      {/* Page Header */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-200 flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-purple-100 text-purple-700 rounded-xl">
              <Bot className="w-5 h-5" />
            </div>
            <h1 className="text-2xl font-black text-gray-900 tracking-tight">AI Assistant</h1>
            {selectedProject && (
              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200">
                {selectedProject.name}
              </span>
            )}
          </div>
          <p className="text-xs text-gray-500">
            Interactive scenario simulation, resource reallocation modeling, and conversational project intelligence.
          </p>
        </div>

        {/* Project Selector */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 bg-gray-50 border border-gray-200 rounded-xl px-3 py-1.5 shadow-2xs">
            <label htmlFor="ai-project-select" className="text-[11px] font-bold text-gray-500 uppercase">
              Project:
            </label>
            <select
              id="ai-project-select"
              className="bg-transparent font-bold text-xs text-gray-800 focus:outline-none cursor-pointer max-w-[220px] truncate"
              value={selectedProjectId || ''}
              onChange={(e) => setSelectedProjectId(Number(e.target.value))}
            >
              {projects.length === 0 ? (
                <option value="">No projects available</option>
              ) : (
                projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>
      </div>

      {/* Loading & Empty States */}
      {loading && (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center text-gray-500 text-xs">
          <RefreshCw className="w-5 h-5 mx-auto mb-2 animate-spin text-purple-600" />
          Loading project workspace...
        </div>
      )}

      {!loading && projects.length === 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center space-y-3">
          <Bot className="w-10 h-10 text-gray-300 mx-auto" />
          <h3 className="text-sm font-bold text-gray-900">No Projects Found</h3>
          <p className="text-xs text-gray-500 max-w-sm mx-auto">
            Create a project to start interacting with the AI Scheduling Assistant.
          </p>
          <Link
            to="/projects"
            className="inline-flex items-center px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-xl text-xs font-semibold shadow-xs"
          >
            Create Project
          </Link>
        </div>
      )}

      {/* Main AI Assistant Panel */}
      {!loading && selectedProjectId && selectedProject && (
        <AiAssistantPanel
          projectId={selectedProjectId}
          projectName={selectedProject.name}
        />
      )}
    </div>
  );
}
