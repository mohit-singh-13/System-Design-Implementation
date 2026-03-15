# 🗄️ Database Scaling Techniques: Read Replicas

This repository is a practical implementation of the **Read Replicas** scaling technique — built as I learn and experiment with the concept. It's not exhaustive, but it covers enough to get a working setup running.

---

## 🎯 Purpose

- To solidify my understanding of database scaling through hands-on implementation.
- To explore how Read Replicas improve performance, reliability, and availability.
- To get comfortable with master-replica configurations.

---

## 💡 Use Case

Read Replicas are commonly used to:

- 📖 Distribute read operations across replicas to reduce load on the master.
- 🔁 Increase data availability and fault tolerance through redundant copies.
- 💾 Enable backup and disaster recovery strategies.

---

## 📁 Structure

- `docker-compose.yaml` — Docker setup for master and replica MySQL containers.
- `master/` & `replica/` — Configuration files for each node.
- `DOCKER-SETUP.md` & `SETUP.md` — Step-by-step instructions to get the environment running.

---

## 🚀 Getting Started

1. Follow `DOCKER-SETUP.md` and `SETUP.md` to set up the environment.
2. Use Docker Compose to launch the master and replica containers.
3. Experiment with replication, failover, and read scaling.

---

## ⚠️ Disclaimer

This is a **personal practice repository**. The Read Replicas setup worked for me, but a few intermediate steps are not fully documented here. Treat this as a **rough outline**, not a step-by-step guide to follow religiously.

If you're trying to replicate it, leverage GenAI to fill in the gaps (as I did!) — and feel free to reach out if you get stuck. 🙌

---

Feel free to fork or explore — and if you're on a similar learning path, happy building! 🛠️
