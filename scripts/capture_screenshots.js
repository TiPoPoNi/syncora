import { spawn } from 'child_process';
import fs from 'fs';
import path from 'path';

const CHROME_PATH = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const OUTPUT_DIR = path.resolve("./docs/screenshots");

if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function run() {
  console.log("1. Authenticating with backend...");
  const authRes = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin' })
  });
  const authData = await authRes.json();
  console.log("Logged in:", authData.username, authData.role);

  console.log("2. Spawning headless Chrome...");
  const chromeProc = spawn(CHROME_PATH, [
    '--headless=new',
    '--remote-debugging-port=9222',
    '--window-size=1440,900',
    '--disable-gpu',
    'http://localhost:5173/'
  ]);

  await delay(2000);

  const listRes = await fetch('http://localhost:9222/json/list');
  const pages = await listRes.json();
  const page = pages.find(p => p.type === 'page');
  console.log("Target page:", page.url, page.webSocketDebuggerUrl);

  const ws = new WebSocket(page.webSocketDebuggerUrl);

  let msgId = 1;
  const pending = new Map();

  ws.onmessage = (e) => {
    const data = JSON.parse(e.data);
    if (data.id && pending.has(data.id)) {
      pending.get(data.id)(data);
      pending.delete(data.id);
    }
  };

  await new Promise(resolve => ws.onopen = resolve);

  function callCdp(method, params = {}) {
    return new Promise(resolve => {
      const id = msgId++;
      pending.set(id, resolve);
      ws.send(JSON.stringify({ id, method, params }));
    });
  }

  await callCdp('Page.enable');
  await callCdp('Runtime.enable');

  console.log("3. Injecting user credentials into localStorage...");
  const token = authData.token;
  const setAuthExpr = `
    localStorage.setItem('prs_user', JSON.stringify({
      token: "${token}",
      username: "admin",
      role: "ADMIN"
    }));
  `;
  await callCdp('Runtime.evaluate', { expression: setAuthExpr });

  // 3b. Seed Phase 13 Cost & Procurement Demo Data
  console.log("3b. Seeding Phase 13 Cost & Procurement demo records...");
  try {
    const projRes = await fetch('http://localhost:8080/api/projects', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const projs = await projRes.json();
    const vendorRes = await fetch('http://localhost:8080/api/vendors', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const vendors = await vendorRes.json();

    if (projs.length > 0) {
      const pId = projs[0].id;
      const vId = vendors.length > 0 ? vendors[0].id : 1;

      // 1. Set budget
      await fetch(`http://localhost:8080/api/projects/${pId}/budget`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ initialBudget: 750000, currency: 'INR', notes: 'Master baseline project budget' })
      });
      // 2. Approve budget
      await fetch(`http://localhost:8080/api/projects/${pId}/budget/approve`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      // 3. Revise budget
      await fetch(`http://localhost:8080/api/projects/${pId}/budget/revisions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ revisedAmount: 850000, reason: 'Scope expansion for payment gateway & high availability database infrastructure' })
      });
      // 4. Quotation
      const quoteRes = await fetch(`http://localhost:8080/api/projects/${pId}/quotations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({
          vendorId: vId,
          quoteReference: 'QT-2026-CLOUD-01',
          issueDate: '2026-10-15',
          validUntil: '2026-11-15',
          currency: 'INR',
          taxAmount: 8500,
          notes: 'High-availability Kubernetes Cluster & Managed DB Instances',
          items: [
            { description: 'Kubernetes Cluster Managed Nodes (6 months)', quantity: 6, unitPrice: 18000 },
            { description: 'PostgreSQL High Availability Primary + Replica', quantity: 1, unitPrice: 42000 }
          ]
        })
      });
      const quoteData = await quoteRes.json();
      if (quoteData && quoteData.id) {
        await fetch(`http://localhost:8080/api/quotations/${quoteData.id}/status`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
          body: JSON.stringify({ status: 'ACCEPTED' })
        });
      }
      // 5. Purchase Order
      const poRes = await fetch(`http://localhost:8080/api/projects/${pId}/purchase-orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({
          vendorId: vId,
          quotationId: quoteData?.id,
          poReference: 'PO-2026-INFRA-8801',
          orderDate: '2026-10-16',
          expectedDeliveryDate: '2026-10-30',
          currency: 'INR',
          notes: 'Priority procurement for Phase 13 production infrastructure',
          lines: [
            { description: 'Production K8s Dedicated Node Instances', quantity: 6, unitPrice: 18000 },
            { description: 'Enterprise SSL & Edge CDN Acceleration', quantity: 1, unitPrice: 25000 }
          ]
        })
      });
      const poData = await poRes.json();
      if (poData && poData.id) {
        // Approve PO
        await fetch(`http://localhost:8080/api/purchase-orders/${poData.id}/status`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
          body: JSON.stringify({ status: 'APPROVED' })
        });
        // Record partial delivery
        if (poData.lines && poData.lines.length > 0) {
          await fetch(`http://localhost:8080/api/purchase-orders/${poData.id}/deliveries`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({
              poLineId: poData.lines[0].id,
              quantityDelivered: 3,
              deliveryReference: 'CHAL-INFRA-1092',
              notes: '3 of 6 nodes provisioned and benchmarked successfully'
            })
          });
        }
      }
      // 6. Expenses
      const expensesToRecord = [
        { category: 'MATERIALS', amount: 95000, description: 'Server hardware and high-speed NVMe SSD drives', expenseDate: '2026-10-12' },
        { category: 'LABOR', amount: 140000, description: 'Full-stack engineering sprint deliverables wages', expenseDate: '2026-10-14' },
        { category: 'VENDOR_SERVICES', amount: 48000, description: 'Third-party penetration testing and compliance audit', expenseDate: '2026-10-17' },
        { category: 'EQUIPMENT', amount: 32000, description: 'Dedicated load balancer testing appliances', expenseDate: '2026-10-18' }
      ];
      for (const exp of expensesToRecord) {
        await fetch(`http://localhost:8080/api/projects/${pId}/expenses`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
          body: JSON.stringify({
            category: exp.category,
            amount: exp.amount,
            currency: 'INR',
            description: exp.description,
            expenseDate: exp.expenseDate
          })
        });
      }
      // 3c. Seed Phase 14 Deliverables Demo Data
      console.log("3c. Seeding Phase 14 Deliverables & Approval Workflows demo records...");
      try {
        const delivRes = await fetch(`http://localhost:8080/api/projects/${pId}/deliverables`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        const existingDelivs = await delivRes.json();
        if (existingDelivs.length === 0) {
          // 1. Architecture Spec (Under Review)
          const d1Res = await fetch(`http://localhost:8080/api/projects/${pId}/deliverables`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({
              name: 'Enterprise System Architecture Specification',
              description: 'Microservices architecture topology, sequence diagrams, and interface boundaries.',
              deliverableType: 'TECHNICAL_DOCUMENT',
              requiredSubmissionDate: '2026-10-25',
              isClientVisible: true,
              isConfidential: false
            })
          });
          const d1 = await d1Res.json();

          // Upload doc v1
          const formData1 = new FormData();
          const blob1 = new Blob(["%PDF-1.4 Enterprise Architecture Specification Document v1..."], { type: 'application/pdf' });
          formData1.append('file', blob1, 'architecture_spec_v1.pdf');
          formData1.append('versionNotes', 'Initial release covering core engine and API contracts');
          await fetch(`http://localhost:8080/api/deliverables/${d1.id}/documents`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData1
          });

          // Submit & Assign Reviewer
          await fetch(`http://localhost:8080/api/deliverables/${d1.id}/submit`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
          });
          await fetch(`http://localhost:8080/api/deliverables/${d1.id}/reviews`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ reviewerId: 1, requestedDecisionDate: '2026-10-28' })
          });

          // 2. UI Prototype (Approved)
          const d2Res = await fetch(`http://localhost:8080/api/projects/${pId}/deliverables`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({
              name: 'High-Fidelity Dashboard & Gantt UI Prototype',
              description: 'Interactive Figma design system, tokens, responsive layouts, and accessibility guidelines.',
              deliverableType: 'UI_PROTOTYPE',
              requiredSubmissionDate: '2026-10-15',
              isClientVisible: true,
              isConfidential: false
            })
          });
          const d2 = await d2Res.json();
          const formData2 = new FormData();
          const blob2 = new Blob(["%PDF-1.4 UI Prototype Package v1..."], { type: 'application/pdf' });
          formData2.append('file', blob2, 'ui_prototype_v1.pdf');
          formData2.append('versionNotes', 'Final review prototype with full component library');
          await fetch(`http://localhost:8080/api/deliverables/${d2.id}/documents`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData2
          });
          await fetch(`http://localhost:8080/api/deliverables/${d2.id}/submit`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
          });
          await fetch(`http://localhost:8080/api/deliverables/${d2.id}/reviews/decision`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({
              decision: 'APPROVED',
              comments: 'Meets all design system criteria and accessibility standards. Approved for production.'
            })
          });

          // 3. Security Pentest Report (Changes Requested)
          const d3Res = await fetch(`http://localhost:8080/api/projects/${pId}/deliverables`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({
              name: 'Application Penetration Testing & Threat Model',
              description: 'OWASP Top 10 vulnerability assessment and mitigation matrix.',
              deliverableType: 'TESTING_REPORT',
              requiredSubmissionDate: '2026-10-18',
              isClientVisible: false,
              isConfidential: true
            })
          });
          const d3 = await d3Res.json();
          const formData3 = new FormData();
          const blob3 = new Blob(["%PDF-1.4 Security Assessment Report v1..."], { type: 'application/pdf' });
          formData3.append('file', blob3, 'security_report_v1.pdf');
          formData3.append('versionNotes', 'Preliminary penetration test findings');
          await fetch(`http://localhost:8080/api/deliverables/${d3.id}/documents`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData3
          });
          await fetch(`http://localhost:8080/api/deliverables/${d3.id}/submit`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
          });
          await fetch(`http://localhost:8080/api/deliverables/${d3.id}/reviews/decision`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({
              decision: 'CHANGES_REQUESTED',
              comments: 'Please document remediation steps for rate limiting and CSRF protection in Section 3.'
            })
          });

          // 4. Client Handover (Overdue)
          const d4Res = await fetch(`http://localhost:8080/api/projects/${pId}/deliverables`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({
              name: 'Client Acceptance & Final Handover Package',
              description: 'Production operational runbooks, user credentials handover, and SLA agreements.',
              deliverableType: 'CLIENT_HANDOVER',
              requiredSubmissionDate: '2026-10-10',
              isClientVisible: true,
              isConfidential: false
            })
          });
          const d4 = await d4Res.json();
          const formData4 = new FormData();
          const blob4 = new Blob(["%PDF-1.4 Handover Manual v1..."], { type: 'application/pdf' });
          formData4.append('file', blob4, 'handover_runbook_v1.pdf');
          formData4.append('versionNotes', 'Draft operational runbook');
          await fetch(`http://localhost:8080/api/deliverables/${d4.id}/documents`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData4
          });
          await fetch(`http://localhost:8080/api/deliverables/${d4.id}/submit`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
          });
          await fetch(`http://localhost:8080/api/deliverables/${d4.id}/reviews`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ reviewerId: 1, requestedDecisionDate: '2026-10-12' })
          });

          // Configure Task Gate on task 1
          const tasksRes = await fetch(`http://localhost:8080/api/tasks?projectId=${pId}`, {
            headers: { 'Authorization': `Bearer ${token}` }
          });
          const tasks = await tasksRes.json();
          if (tasks.length > 0) {
            await fetch(`http://localhost:8080/api/tasks/${tasks[0].id}/approval-gates`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
              body: JSON.stringify({
                deliverableId: d2.id,
                gateType: 'COMPLETION_GATE',
                isMandatory: true
              })
            });
          }

          console.log("✓ Phase 14 deliverables demo data seeded successfully!");
        }
      } catch (err) {
        console.warn("Phase 14 seeding warning:", err.message);
      }

      // ==========================================
      // Phase 15: External Integrations Demo Data
      // ==========================================
      try {
        console.log("Seeding Phase 15 integrations demo data...");
        const authHeaders = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
        // 1. Connect Google Calendar
        await fetch('http://localhost:8080/api/integrations/calendars/GOOGLE_CALENDAR/connect', {
          method: 'POST',
          headers: authHeaders,
          body: JSON.stringify({ accountEmail: 'devops-team@acmeglobal.com', calendarId: 'primary' })
        });

        // 2. Trigger Project Calendar Sync
        const projRes15 = await fetch('http://localhost:8080/api/projects', { headers: authHeaders });
        const projects = await projRes15.json();
        if (projects && projects.length > 0) {
          await fetch(`http://localhost:8080/api/projects/${projects[0].id}/calendar/sync?provider=GOOGLE_CALENDAR`, {
            method: 'POST',
            headers: authHeaders
          });
        }

        // 3. Create Demo Webhooks
        await fetch('http://localhost:8080/api/integrations/webhooks', {
          method: 'POST',
          headers: authHeaders,
          body: JSON.stringify({
            name: 'Enterprise Slack Notifications',
            destinationUrl: 'https://api.example.com/webhooks/notifications-channel',
            isEnabled: true,
            subscribedEvents: ['project.created', 'task.completed', 'deliverable.approved', 'schedule.recalculated']
          })
        });

        await fetch('http://localhost:8080/api/integrations/webhooks', {
          method: 'POST',
          headers: authHeaders,
          body: JSON.stringify({
            name: 'SAP ERP Financial Outbox',
            destinationUrl: 'https://httpbin.org/post',
            isEnabled: true,
            subscribedEvents: ['budget.revised', 'deliverable.approved']
          })
        });

        // 4. Create Scoped API Keys
        await fetch('http://localhost:8080/api/integrations/api-keys', {
          method: 'POST',
          headers: authHeaders,
          body: JSON.stringify({
            name: 'GitHub Actions Deployment Agent',
            scopes: ['projects:read', 'schedules:read'],
            expiresInDays: 90
          })
        });

        await fetch('http://localhost:8080/api/integrations/api-keys', {
          method: 'POST',
          headers: authHeaders,
          body: JSON.stringify({
            name: 'External BI PowerBI Connector',
            scopes: ['projects:read', 'deliverables:read'],
            expiresInDays: 365
          })
        });

        console.log("✓ Phase 15 external integrations demo data seeded successfully!");
      } catch (err) {
        console.warn("Phase 15 seeding warning:", err.message);
      }
    }
  } catch (err) {
    console.warn("Seeding warning:", err.message);
  }

  const targets = [
    { name: 'dashboard.png', path: '/', wait: 2500 },
    { name: 'projects_ledger.png', path: '/projects', wait: 2500 },
    { name: 'clients_directory.png', path: '/clients', wait: 2500 },
    { name: 'workforce_roster.png', path: '/workers', wait: 2500 },
    { name: 'tasks_ledger.png', path: '/tasks', wait: 2500 },
    { name: 'gantt_timeline.png', path: '/schedule?tab=gantt', wait: 2500 },
    { name: 'critical_path.png', path: '/schedule?tab=cpm', wait: 2500 },
    { name: 'delay_analysis.png', path: '/schedule?tab=delays', wait: 2500 },
    { name: 'resource_conflicts.png', path: '/schedule?tab=conflicts', wait: 2500 },
    { name: 'optimization_dashboard.png', path: '/schedule?tab=optimization', wait: 3000 },
    { name: 'ai_assistant.png', path: '/schedule?tab=assistant', wait: 2500 },
    { name: 'historical_analytics.png', path: '/schedule?tab=analytics', wait: 2500 },
    { name: 'notification_workflow.png', path: '/schedule?tab=gantt', wait: 2500 },
    { name: 'vendor_import_integration.png', path: '/vendors', wait: 2500 },
    { name: 'portfolio_analytics.png', path: '/analytics', wait: 3000 },
    { name: 'organization_workspaces.png', path: '/organization', wait: 3000 },
    { name: 'budget_cost_management.png', path: '/budget', wait: 3000 },
    { name: 'deliverables_approval_workflow.png', path: '/deliverables', wait: 3000 },
    { name: 'external_integrations.png', path: '/integrations', wait: 3000 }
  ];

  for (const t of targets) {
    console.log(`Navigating to ${t.path} -> ${t.name}...`);
    await callCdp('Runtime.evaluate', { expression: `window.location.href = 'http://localhost:5173${t.path}';` });
    await delay(t.wait);

    const res = await callCdp('Page.captureScreenshot', { format: 'png' });
    if (res.result && res.result.data) {
      const filePath = path.join(OUTPUT_DIR, t.name);
      fs.writeFileSync(filePath, Buffer.from(res.result.data, 'base64'));
      console.log(`✓ Saved ${t.name} (${Math.round(res.result.data.length * 0.75 / 1024)} KB)`);
    } else {
      console.error(`✗ Error capturing ${t.name}:`, res);
    }
  }

  // Capture Login Screen by clearing auth
  console.log("Navigating to /login -> login_screen.png...");
  await callCdp('Runtime.evaluate', { expression: `localStorage.removeItem('prs_user'); window.location.href = 'http://localhost:5173/login';` });
  await delay(2500);
  const loginRes = await callCdp('Page.captureScreenshot', { format: 'png' });
  if (loginRes.result && loginRes.result.data) {
    fs.writeFileSync(path.join(OUTPUT_DIR, 'login_screen.png'), Buffer.from(loginRes.result.data, 'base64'));
    console.log(`✓ Saved login_screen.png (${Math.round(loginRes.result.data.length * 0.75 / 1024)} KB)`);
  }

  ws.close();
  chromeProc.kill();
  console.log("All screenshots successfully captured!");
}

run().catch(err => {
  console.error(err);
  process.exit(1);
});
