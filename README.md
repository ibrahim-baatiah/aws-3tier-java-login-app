# DevOps Project 01 — Java Login App on AWS (3-Tier)

A Java login/register web app deployed on AWS as a production-style **3-tier architecture**: Nginx (web) → Tomcat (app) → MySQL on RDS, with Redis for shared sessions. It also has load balancers, auto scaling, HTTPS on a custom domain, CloudFront, a build pipeline (Maven → SonarCloud → JFrog), secrets kept in AWS Secrets Manager, and CloudWatch monitoring with email alerts.

🔗 **Live:** [app.ibrahimdev.cloud](https://app.ibrahimdev.cloud)

> Based on [NotHarshhaa/DevOps-Projects](https://github.com/NotHarshhaa/DevOps-Projects). The guide uses the AWS CLI; I built everything in the **AWS Console** to learn each service, and fixed several places where the guide didn't match the app code (see [below](#-where-i-diverged-from-the-guide)).

---

## 🏗️ Architecture

### Request flow

![Request flow](images/network-flow.png)

1. **Cloudflare DNS** points `app.ibrahimdev.cloud` to **CloudFront**.
2. CloudFront serves `/static/*` from a private **S3** bucket (cached at the edge) and forwards everything else to the origin, adding a secret `X-Origin-Verify` header.
3. The **public NLB** ends TLS (ACM certificate) and sends HTTPS traffic to **Nginx :81** and plain HTTP to **Nginx :80** (redirect to https).
4. Nginx rejects requests without the secret header (403), then proxies to the **internal NLB**.
5. The internal NLB spreads requests across two **Tomcat** servers, which use **Redis** for sessions and **RDS MySQL** for users.

### AWS view

![AWS architecture](images/aws-architecture.png)

- **Two VPCs** connected by a **Transit Gateway**: `PrimaryVPC` (192.168.0.0/16) for the app, `Secondary VPC` (172.32.0.0/16) for the SSH jump server.
- **Two Availability Zones**, each with a public and a private subnet.
- Public subnets hold only the public NLB and the NAT Gateway. **All servers, RDS and Redis are private** (no public IPs).
- Nginx and Tomcat each run in an **Auto Scaling group** (min 1 / desired 2 / max 2) built from custom AMIs.

---

## 🧰 Tech Stack

| Area       | Tools                                                                                       |
| ---------- | ------------------------------------------------------------------------------------------- |
| Cloud      | AWS (us-east-1)                                                                             |
| Networking | VPC, subnets, route tables, Internet Gateway, NAT Gateway, Transit Gateway, security groups |
| Compute    | EC2, AMIs, launch templates, Auto Scaling groups, Network Load Balancers                    |
| App        | Java 11, Spring Boot, Apache Tomcat 9, Nginx                                                |
| Data       | Amazon RDS (MySQL), ElastiCache (Redis), S3                                                 |
| Edge & TLS | CloudFront, ACM, Cloudflare DNS                                                             |
| Security   | IAM roles, Secrets Manager, Origin Access Control                                           |
| CI         | Maven, SonarCloud, JFrog Artifactory                                                        |
| Monitoring | CloudWatch agent, logs, metric filters, alarms, dashboard, SNS email alerts                 |

---

## 🚀 Build & Deploy

The build runs on my machine; the servers only run the finished `.war`.

```bash
# 1. Load credentials from AWS Secrets Manager (nothing secret in files)
export SONAR_TOKEN=$(aws secretsmanager get-secret-value --secret-id devops/sonar-token \
  --query SecretString --output text | jq -r '.SONAR_TOKEN')
export JFROG_USERNAME=$(aws secretsmanager get-secret-value --secret-id devops/jfrog-credentials \
  --query SecretString --output text | jq -r '.JFROG_USERNAME')
export JFROG_TOKEN=$(aws secretsmanager get-secret-value --secret-id devops/jfrog-credentials \
  --query SecretString --output text | jq -r '.JFROG_TOKEN')

# 2. Build, test, scan (wait for the quality gate) and publish to JFrog
mvn -f pom.xml clean verify sonar:sonar deploy -s settings.xml -Dsonar.qualitygate.wait=true
```

**Rolling out a new version:** update one server → create a new AMI → new launch template version (set as default) → **instance refresh** on the Auto Scaling group.

At startup, each Tomcat server fetches its DB credentials from Secrets Manager through a systemd `ExecStartPre` script, using the server's IAM role. No passwords live in Git, the `.war`, JFrog or the AMIs.

---

## 🔒 Security Highlights

- All servers and data stores are in private subnets. Admin access goes through a jump server (SSH from my IP only, with agent forwarding).
- The origin only accepts traffic that comes through CloudFront (secret header check in Nginx).
- The S3 bucket is fully private and readable only by CloudFront (OAC).
- IAM roles are least-privilege: only the actions needed, on specific resources.
- App fixes: BCrypt password hashing, `PreparedStatement` instead of string-built SQL, and a fix for a bug where one successful login let any password work afterwards.

---

## 📈 Monitoring

- The CloudWatch agent on every server ships Tomcat/Nginx logs and memory/disk metrics.
- 10 alarms (memory, CPU, unhealthy targets, RDS CPU/storage, DB errors, CloudFront 5xx) send email through SNS.
- The `dptweb-overview` dashboard shows health per tier plus a live table of recent errors.
- **Tested:** I stopped Tomcat on one server. The alarm fired, the site stayed up, and the Auto Scaling group replaced the server automatically.

---

## 🛠️ Where I Diverged from the Guide

| Guide                                   | What was actually needed                                                                       |
| --------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Creates `javaapp` DB / `users` table    | The app uses `UserDB` / `Employee`                                                             |
| Bundles Tomcat's JSP jars in the `.war` | Marked `provided` and excluded `tomcat-embed-el` (duplicate-jar crash)                         |
| Doesn't mention Spring Security         | It locked every page (401), so I removed it and kept only `spring-security-crypto` for hashing |
| Config points to the author's RDS/JFrog | Moved to environment variables + Secrets Manager                                               |
| Secondary VPC has no purpose            | Hosts the jump server, reached through the Transit Gateway                                     |
| Nginx serves `/static/`                 | CloudFront in front of the whole site, with `/static/*` from S3                                |

---

## 💡 Key Lessons

- A subnet is "public" **only** because its route table points `0.0.0.0/0` to an Internet Gateway, and a server there still needs a public IP.
- An NLB only routes to AZs it has a subnet in, and it can't redirect HTTP → HTTPS.
- When TLS ends at the load balancer, the app sees `http://`. Nginx's `proxy_redirect` and `X-Forwarded-Proto` fix the redirects.
- Servers in an Auto Scaling group are disposable. Every change goes into a new AMI, never just onto a running server.
- Testing layer by layer (Tomcat → internal NLB → Nginx → public URL) finds problems fast.

---

## 🙏 Acknowledgements

This project is based on **DevOps-Project-01** from [**NotHarshhaa/DevOps-Projects**](https://github.com/NotHarshhaa/DevOps-Projects) by [@NotHarshhaa](https://github.com/NotHarshhaa). Huge thanks for putting together such a practical, hands-on collection of DevOps projects. The original guide and the Java app gave me the foundation to learn from, and everything here builds on that work.

If you find this useful, please ⭐ the [original repository](https://github.com/NotHarshhaa/DevOps-Projects) too.
