import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useOrganization } from '../context/OrganizationContext';
import {
  Bot,
  Send,
  X,
  Sparkles,
  RefreshCw,
  Maximize2,
  Trash2,
  ChevronDown,
  Info,
  ArrowRight,
  Sliders
} from 'lucide-react';

interface Project {
  id: number;
  name: string;
}

interface Message {
  id?: number;
  role: 'user' | 'assistant';
  content: string;
  modelUsed?: string;
  sourceCitations?: string[];
  createdAt?: string;
}

export function AiChatbotWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputPrompt, setInputPrompt] = useState('');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<string>('ONLINE');

  const { currentOrg } = useOrganization();
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Fetch projects when organization changes
  useEffect(() => {
    let isMounted = true;
    api.get('/projects')
      .then((res) => {
        if (!isMounted) return;
        const list = res.data || [];
        setProjects(list);
        if (list.length > 0 && !selectedProjectId) {
          setSelectedProjectId(list[0].id);
        }
      })
      .catch((err) => {
        console.error('Failed to load projects for Chatbot Widget:', err);
      });

    return () => {
      isMounted = false;
    };
  }, [currentOrg?.id]);

  // Load chat history when project changes or widget is opened
  useEffect(() => {
    if (selectedProjectId && isOpen) {
      loadHistory(selectedProjectId);
      checkStatus(selectedProjectId);
    }
  }, [selectedProjectId, isOpen]);

  // Auto-scroll messages to bottom
  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, loading, isOpen]);

  const checkStatus = async (projectId: number) => {
    try {
      const res = await api.get(`/projects/${projectId}/assistant/status`);
      setStatus(res.data?.status || 'ONLINE');
    } catch {
      setStatus('OFFLINE_FALLBACK');
    }
  };

  const loadHistory = async (projectId: number) => {
    const selectedProjName = projects.find((p) => p.id === projectId)?.name || 'Project';
    try {
      const res = await api.get(`/projects/${projectId}/assistant/history`);
      if (res.data && res.data.length > 0) {
        setMessages(res.data);
      } else {
        setMessages([
          {
            role: 'assistant',
            content: `Hello! I am your AI Project Assistant for **${selectedProjName}**. You can ask me about critical path tasks, delays, resource allocations, or schedule optimization.`,
            modelUsed: 'Grounded Assistant',
          },
        ]);
      }
    } catch (err) {
      setMessages([
        {
          role: 'assistant',
          content: `Hello! I am your AI Project Assistant for **${selectedProjName}**. How can I help with your schedule today?`,
          modelUsed: 'Fallback Engine',
        },
      ]);
    }
  };

  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputPrompt).trim();
    if (!text || loading || !selectedProjectId) return;

    const userMsg: Message = { role: 'user', content: text };
    setMessages((prev) => [...prev, userMsg]);
    setInputPrompt('');
    setLoading(true);

    try {
      const res = await api.post(`/projects/${selectedProjectId}/assistant/messages`, {
        message: text,
      });
      setMessages((prev) => [...prev, res.data]);
    } catch (err: any) {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: `⚠️ Failed to get answer: ${err.response?.data?.message || err.message}`,
          modelUsed: 'System Error',
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleClearHistory = async () => {
    if (!selectedProjectId) return;
    try {
      await api.delete(`/projects/${selectedProjectId}/assistant/history`);
      const selectedProjName = projects.find((p) => p.id === selectedProjectId)?.name || 'Project';
      setMessages([
        {
          role: 'assistant',
          content: `Chat history cleared. How can I assist with **${selectedProjName}**?`,
          modelUsed: 'Grounded Assistant',
        },
      ]);
    } catch (err) {
      console.error('Failed to clear history:', err);
    }
  };

  const promptShortcuts = [
    'Why is this project delayed?',
    'Show critical path tasks',
    'Unresolved resource conflicts?',
    'Compare baseline vs forecast',
  ];

  const selectedProjName = projects.find((p) => p.id === selectedProjectId)?.name || 'Project';

  return (
    <>
      {/* Floating Trigger Button (Bottom-Right) */}
      <div className="fixed bottom-6 right-6 z-50">
        {!isOpen ? (
          <button
            onClick={() => setIsOpen(true)}
            className="group flex items-center gap-2.5 bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white px-4 py-3 rounded-full shadow-xl hover:shadow-2xl transition-all duration-300 hover:scale-105 active:scale-95 focus:outline-none focus:ring-4 focus:ring-purple-300"
            aria-label="Open AI Assistant Chatbot"
            title="Chat with SYNCORA AI Assistant"
          >
            <div className="relative">
              <Bot className="w-5 h-5 text-white animate-pulse" />
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-emerald-400 border-2 border-white rounded-full" />
            </div>
            <span className="font-bold text-xs tracking-wide pr-1">Ask AI</span>
            <Sparkles className="w-3.5 h-3.5 text-purple-200 group-hover:rotate-12 transition-transform" />
          </button>
        ) : null}
      </div>

      {/* Floating Chatbot Window */}
      {isOpen && (
        <div className="fixed bottom-6 right-6 w-96 sm:w-[420px] h-[580px] max-h-[calc(100vh-100px)] bg-white rounded-2xl shadow-2xl border border-gray-200 z-50 flex flex-col overflow-hidden animate-in fade-in slide-in-from-bottom-6 duration-200">
          {/* Header */}
          <div className="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white p-3.5 flex items-center justify-between border-b border-indigo-900/40 select-none">
            <div className="flex items-center space-x-2.5">
              <div className="w-8 h-8 rounded-xl bg-indigo-600/40 border border-indigo-400/40 flex items-center justify-center">
                <Bot className="w-4 h-4 text-indigo-300" />
              </div>
              <div>
                <div className="flex items-center space-x-1.5">
                  <span className="font-bold text-xs tracking-tight">SYNCORA AI</span>
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                </div>
                <div className="text-[10px] text-indigo-200/80">Project Copilot</div>
              </div>
            </div>

            {/* Project Selector & Window Actions */}
            <div className="flex items-center space-x-1.5">
              {projects.length > 0 && (
                <select
                  value={selectedProjectId || ''}
                  onChange={(e) => setSelectedProjectId(Number(e.target.value))}
                  className="bg-white/10 hover:bg-white/20 text-white border border-white/20 rounded-lg text-[10px] font-semibold px-2 py-1 max-w-[130px] truncate focus:outline-none cursor-pointer"
                  title="Switch project context"
                >
                  {projects.map((p) => (
                    <option key={p.id} value={p.id} className="text-gray-900 bg-white">
                      {p.name}
                    </option>
                  ))}
                </select>
              )}

              <button
                onClick={handleClearHistory}
                className="p-1 hover:bg-white/10 rounded-lg text-slate-300 hover:text-white transition"
                title="Clear conversation"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>

              <Link
                to="/ai-assistant"
                onClick={() => setIsOpen(false)}
                className="p-1 hover:bg-white/10 rounded-lg text-slate-300 hover:text-white transition"
                title="Open full page view"
              >
                <Maximize2 className="w-3.5 h-3.5" />
              </Link>

              <button
                onClick={() => setIsOpen(false)}
                className="p-1 hover:bg-white/10 rounded-lg text-slate-300 hover:text-white transition"
                title="Close chatbot"
                aria-label="Close chatbot"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Quick Prompts Chips */}
          <div className="bg-slate-50 border-b border-gray-100 p-2 flex items-center space-x-1.5 overflow-x-auto no-scrollbar">
            <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider pl-1 flex-shrink-0">
              Suggestions:
            </span>
            {promptShortcuts.map((q, idx) => (
              <button
                key={idx}
                onClick={() => handleSendMessage(q)}
                disabled={loading}
                className="text-[10px] whitespace-nowrap bg-white hover:bg-indigo-50 hover:text-indigo-700 text-gray-600 px-2.5 py-1 rounded-full border border-gray-200 transition font-medium flex-shrink-0 disabled:opacity-50"
              >
                {q}
              </button>
            ))}
          </div>

          {/* Message Thread Body */}
          <div className="flex-1 p-4 overflow-y-auto space-y-3.5 bg-gray-50/40 text-xs">
            {messages.map((msg, index) => {
              const isUser = msg.role === 'user';
              return (
                <div
                  key={index}
                  className={`flex items-start gap-2.5 ${isUser ? 'flex-row-reverse' : ''}`}
                >
                  <div
                    className={`w-7 h-7 rounded-lg flex items-center justify-center font-bold text-[10px] flex-shrink-0 shadow-2xs ${
                      isUser
                        ? 'bg-blue-600 text-white'
                        : 'bg-indigo-600 text-white'
                    }`}
                  >
                    {isUser ? 'PM' : <Bot className="w-3.5 h-3.5" />}
                  </div>

                  <div className={`max-w-[80%] space-y-1`}>
                    <div
                      className={`p-3 rounded-2xl text-xs leading-relaxed shadow-2xs ${
                        isUser
                          ? 'bg-blue-600 text-white rounded-tr-none'
                          : 'bg-white text-gray-800 border border-gray-200 rounded-tl-none'
                      }`}
                    >
                      <div className="whitespace-pre-wrap">{msg.content}</div>
                    </div>

                    {!isUser && msg.sourceCitations && msg.sourceCitations.length > 0 && (
                      <div className="text-[9px] text-gray-400 flex items-center gap-1 pl-1">
                        <Info className="w-2.5 h-2.5" />
                        <span>Source: {msg.sourceCitations.join(', ')}</span>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}

            {loading && (
              <div className="flex items-center space-x-2 text-xs text-gray-400 p-2">
                <div className="w-6 h-6 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center">
                  <Bot className="w-3.5 h-3.5 animate-spin" />
                </div>
                <span>Analyzing project CPM data and generating answer...</span>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>

          {/* Input Footer */}
          <div className="p-3 bg-white border-t border-gray-200">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleSendMessage();
              }}
              className="flex items-center gap-2"
            >
              <input
                type="text"
                placeholder={`Ask about ${selectedProjName}...`}
                value={inputPrompt}
                onChange={(e) => setInputPrompt(e.target.value)}
                disabled={loading || !selectedProjectId}
                className="flex-1 text-xs px-3.5 py-2.5 bg-gray-50 border border-gray-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 font-medium"
              />
              <button
                type="submit"
                disabled={!inputPrompt.trim() || loading || !selectedProjectId}
                className="p-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 text-white rounded-xl shadow-xs transition"
                aria-label="Send message"
              >
                <Send className="w-4 h-4" />
              </button>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
