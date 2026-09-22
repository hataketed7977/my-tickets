import { Navigate, Route, Routes } from 'react-router-dom';

import Layout from '@components/Layout';
import NotFound from '@pages/NotFound/NotFound';
import TicketsPage from '@pages/tickets/TicketsPage';
import CategoriesPage from '@pages/categories/CategoriesPage';

const RoutesComponent = () => {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/tickets" replace />} />
        <Route path="tickets" element={<TicketsPage />} />
        <Route
          path="tickets/new"
          element={<TicketsPage initialCreateOpen />}
        />
        <Route path="tickets/:ticketId" element={<TicketsPage />} />
        <Route path="categories" element={<CategoriesPage />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

export default RoutesComponent;
