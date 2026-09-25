# infra/ - deployment infrastructure for the backend EC2

Layout follows Huvo_Backend_Context.md Section 11.

## systemd/

One unit per deployable service (Section 9), plus `service.env.example` - the template for
the `EnvironmentFile` each unit reads.

Install on the backend EC2 (once the units are stable they are created by the
CloudFormation `UserData` script - Section 8.2):

```bash
sudo install -d -o huvo -g huvo /opt/huvo /etc/huvo
sudo cp infra/systemd/*.service /etc/systemd/system/
sudo cp infra/systemd/service.env.example /etc/huvo/employee-service.env   # then fill from SSM
sudo systemctl daemon-reload
sudo systemctl enable --now huvo-employee-service.service
```

`/etc/huvo/*.env` is generated from SSM Parameter Store at boot and is never committed
(Sections 4.3, 9).

## CloudFormation

**Deliberately not written yet.** Section 12 puts it in Phase 6 ("explicitly deferred - ask
for it once Phases 1-3 are stable enough to know real resource sizing"), and Section 8.2 only
records what it *will* provision: VPC + security groups, 2 EC2 instances with `UserData`,
IAM instance roles (S3 + DynamoDB + SSM Parameter Store + CloudWatch Logs, least privilege),
2 Elastic IPs, RDS MySQL `db.t4g.micro`, the 3 DynamoDB tables, the S3 bucket, and a
CloudWatch Log Group.

## Local development

No Docker anywhere (Section 8.1):

- MySQL and RabbitMQ installed **natively**; one local MySQL instance with the 4 schemas from
  `../init-mysql.sql`.
- DynamoDB and S3 are **not emulated** - use real low-cost `huvo-dev-*` resources via a local
  `AWS_PROFILE`.
- Each service is started on its own: `mvn spring-boot:run` (add
  `-Dspring-boot.run.profiles=local` with a gitignored `application-local.yml` for overrides).
