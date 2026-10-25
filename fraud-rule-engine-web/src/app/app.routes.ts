import { Routes } from '@angular/router';
import { authenticatedGuard, localAuthGuard, permissionGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'register',
    title: 'Request an account | Fraud Rule Engine',
    canActivate: [localAuthGuard],
    loadComponent: () =>
      import('./features/identity/register-page.component').then((module) => module.RegisterPageComponent),
  },
  {
    path: 'login',
    title: 'Sign in | Fraud Rule Engine',
    loadComponent: () =>
      import('./features/auth/login-page.component').then((module) => module.LoginPageComponent),
  },
  {
    path: 'forbidden',
    title: 'Access restricted | Fraud Rule Engine',
    loadComponent: () =>
      import('./features/error/forbidden-page.component').then(
        (module) => module.ForbiddenPageComponent,
      ),
  },
  {
    path: '',
    canActivate: [authenticatedGuard],
    canActivateChild: [permissionGuard],
    loadComponent: () =>
      import('./layout/app-shell.component').then((module) => module.AppShellComponent),
    children: [
      {
        path: 'overview',
        title: 'Overview | Fraud Rule Engine',
        data: { permission: 'REPORTS_READ' },
        loadComponent: () =>
          import('./features/evaluations/overview-page.component').then(
            (module) => module.OverviewPageComponent,
          ),
      },
      {
        path: 'evaluations',
        title: 'Evaluations | Fraud Rule Engine',
        data: { permission: 'EVALUATIONS_READ' },
        loadComponent: () =>
          import('./features/evaluations/evaluation-search-page.component').then(
            (module) => module.EvaluationSearchPageComponent,
          ),
      },
      {
        path: 'evaluations/:evaluationId',
        title: 'Evaluation detail | Fraud Rule Engine',
        data: { permission: 'EVALUATIONS_READ' },
        loadComponent: () =>
          import('./features/evaluations/evaluation-detail-page.component').then(
            (module) => module.EvaluationDetailPageComponent,
          ),
      },
      {
        path: 'rules',
        title: 'Rules | Fraud Rule Engine',
        data: { permission: 'RULES_READ' },
        loadComponent: () =>
          import('./features/rules/rule-list-page.component').then(
            (module) => module.RuleListPageComponent,
          ),
      },
      {
        path: 'rules/:ruleCode',
        title: 'Rule details | Fraud Rule Engine',
        data: { permission: 'RULES_READ' },
        loadComponent: () =>
          import('./features/rules/rule-detail-page.component').then(
            (module) => module.RuleDetailPageComponent,
          ),
      },
      {
        path: 'users',
        title: 'Users | Fraud Rule Engine',
        canActivate: [localAuthGuard],
        data: { permission: 'USERS_MANAGE' },
        loadComponent: () =>
          import('./features/identity/user-list-page.component').then(
            (module) => module.UserListPageComponent,
          ),
      },
      {
        path: 'users/:id',
        title: 'Manage account | Fraud Rule Engine',
        canActivate: [localAuthGuard],
        data: { permission: 'USERS_MANAGE' },
        loadComponent: () =>
          import('./features/identity/user-detail-page.component').then(
            (module) => module.UserDetailPageComponent,
          ),
      },
      {
        path: 'profile',
        title: 'My profile | Fraud Rule Engine',
        canActivate: [localAuthGuard],
        data: { permission: 'OWN_PROFILE_READ' },
        loadComponent: () =>
          import('./features/identity/profile-page.component').then(
            (module) => module.ProfilePageComponent,
          ),
      },
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
    ],
  },
  { path: '**', redirectTo: '' },
];
