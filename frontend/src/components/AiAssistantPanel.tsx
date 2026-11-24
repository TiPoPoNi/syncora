import React, { useState, useEffect, useRef } from 'react';
import { api } from '../services/api';
import {
  Bot,
  Send,
  Sparkles,
  AlertTriangle,
  Clock,
  CheckCircle,
  HelpCircle,
  ArrowRight,
  ShieldAlert,
  Layers,
  Database,
  RefreshCw,
  Sliders,
  ChevronDown,
  ChevronUp,
  Info
} from 'lucide-react';

interface AiAssistantPanelProps {
  projectId: number;
  projectName: string;
}

interface Message {
  id?: number;
  role: 'user' | 'assistant';
  content: string;
  modelUsed?: string;
  sourceCitations?: string[];
  groundedFactsSnapshot?: any;
  createdAt?: string;
}

interface ScenarioResult {
  scenarioType: string;
  targetTaskId?: number;
  targetTaskName?: string;
  originalForecast: string;
  simulatedForecast: string;
  workingDaysDifference: number;
  originalDeadlineStatus: string;
  simulatedDeadlineStatus: string;
  clientDeadline: string;
  affectedTasks: Array<{
    taskId: number;
    taskName: string;
    originalEnd: string;
    simulatedEnd: string;
    delayDays: number;
  }>;
  conflictsResolvedCount: number;
  newConflictsCount: number;
  isHypothetical: boolean;
  aiExplanation: string;
  impactSummary: string;
  risksAndAssumptions: string[];
  recommendedNextStep: string;
  citations: string[];
}

export function AiAssistantPanel({ projectId, projectName }: AiAssistantPanelProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputPrompt, setInputPrompt] = useState('');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<{ status: string; provider: string; model: string } | null>(null);
  const [expandedFacts, setExpandedFacts] = useState<Record<number, boolean>>({});

  // Scenario Simulation State
  const [showScenarioModal, setShowScenarioModal] = useState(false);
  const [scenarioDelayDays, setScenarioDelayDays] = useState(3);
  const [scenarioReason, setScenarioReason] = useState('Vendor delivery delay of critical hardware component');
  const [simulating, setSimulating] = useState(false);
  const [scenarioResult, setScenarioResult] = useState<ScenarioResult | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Load Assistant Status & Chat History
  useEffect(() => {
    loadAssistantStatus();
    loadChatHistory();
  }, [projectId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const loadAssistantStatus = async () => {
    try {
      const res = await api.get(`/projects/${projectId}/assistant/status`);
      setStatus(res.data);
    } catch (err) {
      setStatus({ status: 'offline', provider: 'deterministic-fallback', model: 'in-memory-rules' });
    }
  };

  const loadChatHistory = async () => {
    try {
      const res = await api.get(`/projects/${projectId}/assistant/history`);
      if (res.data && res.data.length > 0) {
        setMessages(res.data);
      } else {
        // Default welcoming message
        setMessages([
          {
            role: 'assistant',
            content: `Hello! I am your Project Management AI Assistant for **${projectName}**. I have direct access to authoritative schedule calculations, CPM critical paths, task slack, resource assignments, and optimization recommendations. How can I assist your project analysis today?`,
            modelUsed: 'Grounded Assistant',
            sourceCitations: ['System Initializer']
          }
        ]);
      }
    } catch (err) {
      console.error('Failed to load chat history', err);
    }
  };

  const handleSendMessage = async (promptToSend?: string) => {
    const text = (promptToSend || inputPrompt).trim();
    if (!text || loading) return;

    const userMsg: Message = { role: 'user', content: text };
    setMessages((prev) => [...prev, userMsg]);
    setInputPrompt('');
    setLoading(true);

    try {
      const res = await api.post(`/projects/${projectId}/assistant/messages`, { message: text });
      setMessages((prev) => [...prev, res.data]);
    } catch (err: any) {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: `⚠️ Failed to query AI Assistant: ${err.response?.data?.message || err.message}. The system remains fully operational via deterministic views.`,
          modelUsed: 'System Error Handler'
        }
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleRunScenario = async () => {
    setSimulating(true);
    setScenarioResult(null);
    try {
      const res = await api.post(`/projects/${projectId}/assistant/scenarios`, {
        scenarioType: 'TASK_DELAY',
        additionalDelayDays: Number(scenarioDelayDays),
        delayReason: scenarioReason
      });
      setScenarioResult(res.data);
    } catch (err: any) {
      alert('Scenario simulation failed: ' + (err.response?.data?.message || err.message));
    } finally {
      setSimulating(false);
    }
  };

  const promptShortcuts = [
    'Why is this project delayed?',
    'Show tasks on the critical path.',
    'Which tasks have the least scheduling slack?',
    'Explain the latest optimization recommendation.',
    'Compare the baseline with the current forecast.',
    'Which resource conflicts remain unresolved?'
  ];

  return (
    <div className="space-y-6">
      {/* Top Banner & Provider Status */}
      <div className="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white rounded-2xl p-6 shadow-xl border border-indigo-900/40 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center space-x-4">
          <div className="p-3 bg-indigo-600/30 border border-indigo-400/30 rounded-xl backdrop-blur-md">
            <Bot className="w-8 h-8 text-indigo-400 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h2 className="text-xl font-black tracking-tight">AI Project Management Assistant</h2>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                Grounded & Explainable
              </span>
            </div>
            <p className="text-xs text-indigo-200/80 mt-1">
              Deterministic calculations from Java CPM & Optimization Engine passed securely to AI. Read-only intelligence layer.
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={() => setShowScenarioModal(true)}
            className="flex items-center px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-bold transition shadow-lg shadow-indigo-600/30 hover:scale-[1.02] active:scale-[0.98]"
          >
            <Sliders className="w-4 h-4 mr-2" />
            Hypothetical What-If Simulation
          </button>

          <div className="bg-white/10 backdrop-blur-md px-3 py-2 rounded-xl text-xs text-slate-300 border border-white/10 flex items-center space-x-2">
            <div className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
            <span>Provider: <strong className="text-white">{status?.provider || 'Mock Provider'}</strong></span>
          </div>
        </div>
      </div>

      {/* Suggested Prompt Shortcuts */}
      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-2xs">
        <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2 flex items-center">
          <Sparkles className="w-3.5 h-3.5 mr-1.5 text-indigo-500" />
          Suggested Managerial Questions
        </div>
        <div className="flex flex-wrap gap-2">
          {promptShortcuts.map((q, idx) => (
            <button
              key={idx}
              onClick={() => handleSendMessage(q)}
              disabled={loading}
              className="text-xs bg-gray-50 hover:bg-indigo-50 hover:text-indigo-700 text-gray-700 px-3 py-1.5 rounded-lg border border-gray-200 transition font-medium text-left flex items-center disabled:opacity-50"
            >
              <span>{q}</span>
              <ArrowRight className="w-3 h-3 ml-1.5 opacity-50" />
            </button>
          ))}
        </div>
      </div>

      {/* Chat Thread Container */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm flex flex-col h-[560px]">
        {/* Messages Stream */}
        <div className="flex-1 p-6 overflow-y-auto space-y-5">
          {messages.map((msg, index) => (
            <div
              key={index}
              className={`flex items-start space-x-3 ${msg.role === 'user' ? 'flex-row-reverse space-x-reverse' : ''}`}
            >
              {msg.role === 'user' ? (
                <div className="w-9 h-9 rounded-xl bg-blue-600 text-white flex items-center justify-center font-bold text-xs shadow-md shrink-0">
                  PM
                </div>
              ) : (
                <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-800 text-white flex items-center justify-center font-bold text-xs shadow-md shrink-0">
                  <Bot className="w-5 h-5 text-indigo-100" />
                </div>
              )}

              <div className={`max-w-[80%] space-y-2`}>
                <div
                  className={`p-4 rounded-2xl text-sm leading-relaxed shadow-2xs ${
                    msg.role === 'user'
                      ? 'bg-blue-600 text-white font-medium rounded-tr-xs'
                      : 'bg-gray-50 border border-gray-200 text-gray-800 rounded-tl-xs'
                  }`}
                >
                  <div className="whitespace-pre-wrap">{msg.content}</div>

                  {/* Grounded Metadata Badges for Assistant */}
                  {msg.role === 'assistant' && (
                    <div className="mt-3 pt-3 border-t border-gray-200/80 flex flex-wrap items-center justify-between text-[11px] text-gray-400 gap-2">
                      <div className="flex items-center space-x-2">
                        <span className="font-semibold text-gray-600">{msg.modelUsed || 'AI Core'}</span>
                        <span>•</span>
                        <span>Authorized Snapshot</span>
                      </div>

                      {msg.sourceCitations && msg.sourceCitations.length > 0 && (
                        <div className="flex flex-wrap gap-1">
                          {msg.sourceCitations.map((cite, i) => (
                            <span
                              key={i}
                              className="px-2 py-0.5 rounded-md bg-indigo-50 border border-indigo-200 text-indigo-700 font-semibold text-[10px]"
                            >
                              {cite}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Grounded Facts Snapshot Accordion */}
                {msg.groundedFactsSnapshot && (
                  <div className="text-xs">
                    <button
                      onClick={() =>
                        setExpandedFacts((prev) => ({ ...prev, [index]: !prev[index] }))
                      }
                      className="text-gray-500 hover:text-indigo-600 font-semibold flex items-center space-x-1"
                    >
                      <Database className="w-3.5 h-3.5 mr-1" />
                      <span>{expandedFacts[index] ? 'Hide Grounded Context Facts' : 'Inspect Grounded Context Facts'}</span>
                      {expandedFacts[index] ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                    </button>

                    {expandedFacts[index] && (
                      <div className="mt-2 p-3 bg-slate-900 text-slate-200 rounded-xl font-mono text-[11px] overflow-x-auto max-h-48 border border-slate-800">
                        <pre>{JSON.stringify(msg.groundedFactsSnapshot, null, 2)}</pre>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}

          {loading && (
            <div className="flex items-center space-x-3">
              <div className="w-9 h-9 rounded-xl bg-indigo-600 text-white flex items-center justify-center font-bold text-xs shadow-md shrink-0">
                <Bot className="w-5 h-5 text-indigo-100" />
              </div>
              <div className="bg-gray-50 border border-gray-200 p-4 rounded-2xl rounded-tl-xs text-xs text-gray-500 flex items-center space-x-2">
                <RefreshCw className="w-4 h-4 animate-spin text-indigo-600" />
                <span>Retrieving authorized project schedule & generating grounded explanation...</span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <div className="p-4 bg-gray-50 border-t border-gray-200 rounded-b-2xl">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSendMessage();
            }}
            className="flex items-center space-x-3"
          >
            <input
              type="text"
              value={inputPrompt}
              onChange={(e) => setInputPrompt(e.target.value)}
              placeholder="Ask questions about critical paths, slack, vendor delays, optimizer rationale..."
              disabled={loading}
              className="flex-1 bg-white border border-gray-300 rounded-xl px-4 py-2.5 text-sm text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 shadow-2xs"
            />
            <button
              type="submit"
              disabled={loading || !inputPrompt.trim()}
              className="bg-indigo-600 hover:bg-indigo-700 text-white px-5 py-2.5 rounded-xl font-bold text-sm shadow-sm transition flex items-center disabled:opacity-50"
            >
              <Send className="w-4 h-4 mr-1.5" />
              Send
            </button>
          </form>
          <div className="mt-2 text-[11px] text-gray-400 flex items-center justify-between">
            <span>Spring Boot authorization verifies project permissions before context dispatch.</span>
            <span className="font-medium text-gray-500">Read-Only Safety Guardrails Active</span>
          </div>
        </div>
      </div>

      {/* Hypothetical What-If Scenario Simulation Modal */}
      {showScenarioModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full border border-gray-200 p-6 space-y-5 animate-in fade-in duration-150">
            <div className="flex items-center justify-between border-b pb-4">
              <div className="flex items-center space-x-3">
                <div className="p-2.5 bg-indigo-50 rounded-xl text-indigo-600">
                  <Sliders className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg font-black text-gray-900">Hypothetical Schedule Simulation</h3>
                  <p className="text-xs text-gray-500">
                    Runs in-memory CPM calculation without persisting changes to the active database.
                  </p>
                </div>
              </div>
              <button
                onClick={() => setShowScenarioModal(false)}
                className="text-gray-400 hover:text-gray-600 font-bold text-lg"
              >
                ✕
              </button>
            </div>

            {/* Inputs */}
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">
                  Additional Delay (Working Days)
                </label>
                <input
                  type="number"
                  min="1"
                  max="30"
                  value={scenarioDelayDays}
                  onChange={(e) => setScenarioDelayDays(Number(e.target.value))}
                  className="w-full border border-gray-300 rounded-xl p-2.5 text-sm font-semibold"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 uppercase mb-1">
                  Hypothetical Cause / Scenario Description
                </label>
                <input
                  type="text"
                  value={scenarioReason}
                  onChange={(e) => setScenarioReason(e.target.value)}
                  placeholder="e.g. Critical vendor shipment delayed by 3 working days"
                  className="w-full border border-gray-300 rounded-xl p-2.5 text-sm"
                />
              </div>

              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl flex items-start space-x-2 text-xs text-amber-800">
                <ShieldAlert className="w-4 h-4 shrink-0 text-amber-600 mt-0.5" />
                <div>
                  <strong>Safety Notice:</strong> This scenario will simulate the downstream CPM ripple effect in memory. It will <strong>NOT</strong> modify actual project task dates or create active delay records.
                </div>
              </div>

              <button
                onClick={handleRunScenario}
                disabled={simulating}
                className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl text-sm transition shadow-md flex items-center justify-center space-x-2 disabled:opacity-50"
              >
                {simulating ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin mr-2" />
                    <span>Executing In-Memory Java CPM Simulation...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="w-4 h-4 mr-2" />
                    <span>Simulate & Explain Scenario</span>
                  </>
                )}
              </button>
            </div>

            {/* Simulation Results Preview */}
            {scenarioResult && (
              <div className="mt-4 p-5 bg-gray-50 border border-gray-200 rounded-2xl space-y-4">
                <div className="flex items-center justify-between border-b pb-3">
                  <div className="flex items-center space-x-2">
                    <span className="px-2 py-0.5 rounded-full text-[11px] font-black uppercase tracking-wider bg-purple-100 text-purple-700">
                      Hypothetical Simulation Result
                    </span>
                  </div>
                  <div className="text-xs text-gray-500">
                    Calculated Difference: <strong className={scenarioResult.workingDaysDifference > 0 ? 'text-rose-600' : 'text-emerald-600'}>
                      {scenarioResult.workingDaysDifference > 0 ? `+${scenarioResult.workingDaysDifference}` : scenarioResult.workingDaysDifference} working days
                    </strong>
                  </div>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                  <div className="bg-white p-3 rounded-xl border border-gray-200">
                    <div className="text-[10px] font-bold text-gray-400 uppercase">Original Forecast</div>
                    <div className="text-sm font-black text-gray-900">{scenarioResult.originalForecast}</div>
                    <span className="text-[10px] text-gray-500">{scenarioResult.originalDeadlineStatus}</span>
                  </div>

                  <div className="bg-white p-3 rounded-xl border border-gray-200">
                    <div className="text-[10px] font-bold text-gray-400 uppercase">Simulated Forecast</div>
                    <div className="text-sm font-black text-rose-600">{scenarioResult.simulatedForecast}</div>
                    <span className={`text-[10px] font-bold ${scenarioResult.simulatedDeadlineStatus === 'MISSED' ? 'text-rose-600' : 'text-emerald-600'}`}>
                      {scenarioResult.simulatedDeadlineStatus}
                    </span>
                  </div>

                  <div className="bg-white p-3 rounded-xl border border-gray-200">
                    <div className="text-[10px] font-bold text-gray-400 uppercase">Client Deadline</div>
                    <div className="text-sm font-black text-gray-900">{scenarioResult.clientDeadline}</div>
                    <span className="text-[10px] text-gray-500">Target</span>
                  </div>

                  <div className="bg-white p-3 rounded-xl border border-gray-200">
                    <div className="text-[10px] font-bold text-gray-400 uppercase">Affected Tasks</div>
                    <div className="text-sm font-black text-gray-900">{scenarioResult.affectedTasks?.length || 0}</div>
                    <span className="text-[10px] text-gray-500">Downstream</span>
                  </div>
                </div>

                {/* AI Grounded Explanation */}
                <div className="bg-white p-4 rounded-xl border border-gray-200 space-y-2">
                  <div className="flex items-center space-x-1.5 text-xs font-bold text-indigo-700 uppercase tracking-wide">
                    <Bot className="w-4 h-4 text-indigo-600" />
                    <span>AI Scenario Explanation</span>
                  </div>
                  <p className="text-xs text-gray-700 leading-relaxed">
                    {scenarioResult.aiExplanation}
                  </p>
                  {scenarioResult.recommendedNextStep && (
                    <div className="mt-2 text-xs bg-indigo-50 border border-indigo-200 p-2.5 rounded-lg text-indigo-900">
                      <strong>Recommended Action:</strong> {scenarioResult.recommendedNextStep}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
